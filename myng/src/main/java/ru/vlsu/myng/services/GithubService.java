package ru.vlsu.myng.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.vlsu.myng.entities.GameVersion;
import ru.vlsu.myng.utils.GameDownloadException;
import ru.vlsu.myng.utils.GithubException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Сервис для взаимодействия с GitHub API.<br>
 * <br>
 * Обеспечивает интеграцию с GitHub для:<br>
 * - валидации существования репозитория;<br>
 * - проверки наличия коммита в репозитории;<br>
 * - верификации списка файлов в коммите (включая обязательный index.html);<br>
 * - загрузки ZIP-архива репозитория и извлечения необходимых файлов.<br>
 * <br>
 * Используется в следующих сценариях:<br>
 * - разработчик публикует новую игру (проверка репозитория и коммита);<br>
 * - разработчик публикует новую версию игры (проверка файлов);<br>
 * - модератор одобряет версию игры (загрузка файлов на сервер).<br>
 * <br>
 * Все запросы к GitHub API выполняются с аутентификацией через токен,
 * указанный в свойстве {@code github.token}.
 * Для HTTP-запросов используется реактивный {@link WebClient},
 * но вызовы блокируются для синхронной работы в MVC-контексте.
 * Файлы загружаются в директорию, указанную в свойстве {@code app.storage.path}.
 */
@Service
public class GithubService {

    private final WebClient webClient;

    @Value("${github.token}")
    private String githubToken;

    @Value("${app.storage.path}")
    private String storagePath;

    // Лимиты при скачивании игры из GitHub (защита от zip-бомб)
    @Value("${app.games.max-archive-size:104857600}")
    private long maxArchiveSize;

    @Value("${app.games.max-unpacked-size:209715200}")
    private long maxUnpackedSize;

    @Value("${app.games.max-entries:5000}")
    private int maxZipEntries;

    /**
     * Создаёт экземпляр сервиса с настроенным {@link WebClient}.
     *
     * <p>
     * WebClient конфигурируется с:
     * </p>
     * <ul>
     *   <li>базовым URL {@code https://api.github.com};</li>
     *   <li>заголовком авторизации с Bearer-токеном;</li>
     *   <li>заголовком User-Agent;</li>
     *   <li>автоматическим следованием редиректам.</li>
     * </ul>
     *
     * @param builder построитель WebClient, предоставляемый Spring.
     */
    public GithubService(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);
        this.webClient = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .defaultHeader(HttpHeaders.USER_AGENT, "MyNG")
                .filter((request, next) -> {
                    String token = githubToken;
                    ClientRequest newRequest = ClientRequest.from(request)
                            .headers(h -> h.setBearerAuth(token))
                            .build();
                    return next.exchange(newRequest);
                })
                .build();
    }

    /**
     * Проверяет существование GitHub-репозитория по URL.
     *
     * <p>
     * Извлекает владельца и имя репозитория из URL формата
     * {@code https://github.com/[owner]/[repo]},
     * затем выполняет GET-запрос к GitHub API.
     * </p>
     *
     * @param repoUrl URL репозитория в формате https://github.com/owner/repo.
     *                Не должен быть null или пустым.
     *
     * @throws GithubException с полем "repoLink" если:
     *         <ul>
     *           <li>репозиторий не найден (404);</li>
     *           <li>некорректный запрос (400);</li>
     *           <li>другая ошибка GitHub API;</li>
     *           <li>GitHub недоступен.</li>
     *         </ul>
     */
    public void validateRepoExists(String repoUrl) {
        String[] parts = extractPath(repoUrl).split("/");
        System.out.println(Arrays.toString(parts));

        String owner = parts[0];
        String repo = parts[1];

        try {
            webClient.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .onStatus(
                            status -> status == HttpStatus.NOT_FOUND || status == HttpStatus.BAD_REQUEST,
                            response -> Mono.error(new GithubException("repoLink", "Репозиторий не найден"))
                    )
                    .onStatus(
                            HttpStatusCode::isError,
                            response -> response.bodyToMono(String.class).flatMap(body -> {
                                System.out.println("GitHub error response body: " + body);
                                System.out.println("Status code: " + response.statusCode());

                                return Mono.error(new GithubException(
                                        "repoLink",
                                        "GitHub error: " + response.statusCode()
                                ));
                            })
                    )
                    .toBodilessEntity()
                    .block(); // blocking ON PURPOSE (we're in MVC)

        } catch (GithubException e) {
            throw e;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new GithubException("repoLink", "GitHub недоступен, попробуйте позже");
        }
    }

    /**
     * Проверяет существование коммита в указанном репозитории.
     *
     * <p>
     * Выполняет GET-запрос к GitHub API для получения информации о коммите
     * по его хешу. Если коммит не найден (404), выбрасывается исключение.
     * </p>
     *
     * @param repoUrl    URL репозитория в формате https://github.com/owner/repo.
     *                   Не должен быть null или пустым.
     * @param commitHash хеш коммита. Не должен быть null или пустым.
     *
     * @throws GithubException с полем "commitHash" если:
     *         <ul>
     *           <li>коммит не найден в репозитории (404);</li>
     *           <li>коммит не принадлежит данному репозиторию;</li>
     *           <li>другая ошибка GitHub API;</li>
     *           <li>GitHub недоступен.</li>
     *         </ul>
     */
    public void validateCommitExists(String repoUrl, String commitHash) {
        String[] path = extractPath(repoUrl).split("/");

        try {
            webClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{commit}",
                            path[0],
                            path[1],
                            commitHash)
                    .retrieve()

                    .onStatus(
                            status -> status == HttpStatus.NOT_FOUND,
                            response -> Mono.error(new GithubException(
                                    "commitHash",
                                    "Коммит не найден в данном репозитории или не принадлежит ему"
                            ))
                    )

                    .onStatus(
                            HttpStatusCode::isError,
                            response -> Mono.error(new GithubException(
                                    "commitHash",
                                    "Ошибка при проверке коммита"
                            ))
                    )

                    .toBodilessEntity()
                    .block();

        } catch (GithubException e) {
            throw e;
        } catch (Exception e) {
            throw new GithubException("commitHash", "GitHub недоступен, попробуйте позже");
        }
    }

    /**
     * Извлекает путь репозитория из полного URL.
     *
     * <p>
     * Удаляет префикс {@code https://github.com/}
     * и завершающий слеш (если есть).
     * </p>
     *
     * @param url полный URL репозитория.
     *
     * @return путь в формате "owner/repo".
     */
    private String extractPath(String url) {
        return url.replace("https://github.com/", "").replaceAll("/$", "");
    }

    /**
     * Проверяет наличие указанных файлов в коммите репозитория.
     *
     * <p>
     * Алгоритм проверки:
     * </p>
     * <ol>
     *   <li>Получает рекурсивное дерево файлов коммита через Git Trees API;</li>
     *   <li>Проверяет наличие index.html в корне или в одной из указанных папок;</li>
     *   <li>Проверяет существование каждого запрошенного файла/папки;</li>
     *   <li>Если какие-либо файлы отсутствуют — выбрасывает исключение
     *       со списком недостающих.</li>
     * </ol>
     *
     * <p>
     * Файл index.html является обязательным — он используется
     * как точка входа для запуска игры в браузере.
     * </p>
     *
     * @param repoUrl    URL репозитория. Не должен быть null или пустым.
     * @param commitHash хеш коммита. Не должен быть null или пустым.
     * @param files      список файлов через запятую (например, "index.html, game.js, assets/").
     *                   Не должен быть null или пустым.
     *
     * @throws GithubException с полем "files" если:
     *         <ul>
     *           <li>не удалось получить дерево файлов коммита;</li>
     *           <li>index.html отсутствует в списке или в коммите;</li>
     *           <li>указанные файлы/папки не найдены в коммите;</li>
     *           <li>GitHub недоступен.</li>
     *         </ul>
     */
    public void validateFilesExistInCommit(String repoUrl, String commitHash, String files) {
        String[] path = extractPath(repoUrl).split("/");
        String owner = path[0];
        String repo = path[1];

        List<String> requestedPaths = Arrays.stream(files.split("\\s*,\\s*"))
                .map(String::trim)
                .toList();

        try {
            JsonNode response = webClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{sha}?recursive=1",
                            owner,
                            repo,
                            commitHash)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            res -> Mono.error(new GithubException(
                                    "files",
                                    "Ошибка при получении файлов коммита"
                            ))
                    )
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || response.get("tree") == null) {
                throw new GithubException("files", "Не удалось получить дерево файлов коммита");
            }

            List<String> repoPaths = new ArrayList<>();
            for (JsonNode node : response.get("tree")) {
                String type = node.get("type").asText();
                String pathValue = node.get("path").asText();

                if ("blob".equals(type) || "tree".equals(type)) {
                    repoPaths.add(pathValue);
                }
            }

            boolean hasIndexHtml = requestedPaths.stream()
                    .map(p -> p.replaceAll("/+$", "")) // normalize trailing slash
                    .anyMatch(requested ->
                            repoPaths.stream().anyMatch(repoPath ->
                                    repoPath.equals(requested + "/index.html")
                                            || repoPath.equals("index.html")
                                            || repoPath.equals(requested)
                            )
                    );

            if (!hasIndexHtml) {
                throw new GithubException(
                        "files",
                        "Список должен содержать index.html (в корне или в папке)"
                );
            }

            List<String> missing = new ArrayList<>();

            for (String requested : requestedPaths) {
                if (!existsInRepo(repoPaths, requested)) {
                    missing.add(requested);
                }
            }

            if (!missing.isEmpty()) {
                throw new GithubException(
                        "files",
                        "Файлы/папки не найдены в коммите: " + String.join(", ", missing)
                );
            }

        } catch (GithubException e) {
            throw e;
        } catch (Exception e) {
            throw new GithubException("files", "GitHub недоступен, попробуйте позже");
        }
    }

    /**
     * Проверяет существование файла или папки в списке путей репозитория.
     *
     * <p>
     * Поддерживает несколько вариантов совпадения:
     * </p>
     * <ul>
     *   <li>точное совпадение пути;</li>
     *   <li>путь заканчивается на запрошенное имя;</li>
     *   <li>запрошенный путь является родительской директорией
     *       для существующих файлов.</li>
     * </ul>
     *
     * @param repoPaths список всех путей в репозитории.
     * @param requested запрошенный путь для проверки.
     *
     * @return true если путь существует в репозитории.
     */
    private boolean existsInRepo(List<String> repoPaths, String requested) {

        String normalized = requested.endsWith("/")
                ? requested.substring(0, requested.length() - 1)
                : requested;

        boolean exactMatch = repoPaths.stream()
                .anyMatch(path ->
                        path.equals(normalized) ||
                                path.endsWith("/" + normalized)
                );

        if (exactMatch) {
            return true;
        }

        return repoPaths.stream()
                .anyMatch(path ->
                        path.startsWith(normalized + "/") ||
                                path.contains("/" + normalized + "/")
                );
    }

    /**
     * Загружает файлы версии игры из GitHub и сохраняет их на диск.
     *
     * <p>
     * Процесс загрузки:
     * </p>
     * <ol>
     *   <li>Создаёт целевую директорию в файловой системе сервера;</li>
     *   <li>Скачивает ZIP-архив репозитория для указанного коммита;</li>
     *   <li>Извлекает из архива только запрошенные файлы;</li>
     *   <li>Внедряет JavaScript-патч в index.html для интеграции с платформой;</li>
     *   <li>Удаляет временный ZIP-архив.</li>
     * </ol>
     *
     * <p>
     * Целевая директория имеет структуру:
     * </p>
     * <pre>
     * {storagePath}/gamefiles/game_{gameId}/ver_{versionId}/
     * </pre>
     *
     * <p>
     * В index.html автоматически внедряется скрипт с информацией
     * об игре и версии для взаимодействия с API платформы.
     * </p>
     *
     * @param version версия игры, для которой загружаются файлы.
     *                Не должна быть null.
     *
     * @throws RuntimeException если произошла ошибка при загрузке
     *         или извлечении файлов
     */
    public void downloadGameVersion(GameVersion version) {
        String repoUrl = version.getGame().getRepo();
        String commit = version.getCommitHash();

        String[] path = extractPath(repoUrl).split("/");
        String owner = path[0];
        String repo = path[1];

        Path targetDir = buildTargetPath(version);

        System.out.println("Game download target directory: " + targetDir);

        Path zipFile = null;

        try {
            Files.createDirectories(targetDir);

            zipFile = downloadZip(owner, repo, commit);
            extractNeededFiles(zipFile, targetDir, version.getFiles(), version);

        } catch (Exception e) {
            // не оставляем на диске частично распакованную версию
            deleteDirectoryQuietly(targetDir);
            throw new GameDownloadException("Ошибка загрузки файлов из GitHub: " + e.getMessage(), e);
        } finally {
            if (zipFile != null) {
                try {
                    Files.deleteIfExists(zipFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Формирует путь к директории для хранения файлов версии игры.
     *
     * <p>
     * Структура пути: {storagePath}/gamefiles/game_{gameId}/ver_{versionId}
     * </p>
     *
     * @param version версия игры. Не должна быть null.
     *
     * @return путь к директории версии.
     */
    private Path buildTargetPath(GameVersion version) {
        return Paths.get(storagePath,
                "gamefiles",
                "game_" + version.getGame().getId(),
                "ver_" + version.getId());
    }

    /**
     * Скачивает ZIP-архив репозитория для указанного коммита во временный файл.
     *
     * <p>
     * Использует GitHub API endpoint {@code /repos/{owner}/{repo}/zipball/{commit}}
     * для получения архива.
     * </p>
     *
     * @param owner  владелец репозитория.
     * @param repo   имя репозитория.
     * @param commit хеш коммита.
     *
     * @return путь к временному ZIP-файлу.
     *
     * @throws IOException если произошла ошибка при создании или записи файла
     * @throws IllegalStateException если архив больше {@code app.games.max-archive-size}
     */
    private Path downloadZip(String owner, String repo, String commit) throws IOException
    {
        Path tempFile = Files.createTempFile("repo-", ".zip");

        AtomicLong downloaded = new AtomicLong();

        Flux<DataBuffer> flux = webClient.get()
                .uri("/repos/{owner}/{repo}/zipball/{commit}",
                    owner,
                    repo,
                    commit)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                // прерываем скачивание, как только архив превысил лимит
                .handle((buffer, sink) -> {
                    if (downloaded.addAndGet(buffer.readableByteCount()) > maxArchiveSize) {
                        DataBufferUtils.release(buffer);
                        sink.error(new IllegalStateException(
                                "Архив репозитория превышает допустимый размер ("
                                        + toMegabytes(maxArchiveSize) + " МБ)"));
                    } else {
                        sink.next(buffer);
                    }
                });

        try {
            DataBufferUtils.write(flux, tempFile)
                    .block();
        } catch (RuntimeException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }

        return tempFile;
    }

    /**
     * Извлекает из ZIP-архива только запрошенные файлы.
     *
     * <p>
     * Для каждого файла из списка проверяется:
     * </p>
     * <ul>
     *   <li>точное совпадение имени;</li>
     *   <li>вхождение в указанную директорию.</li>
     * </ul>
     *
     * <p>
     * При извлечении index.html в него внедряется JavaScript-патч
     * с идентификаторами игры и версии для взаимодействия с платформой.
     * Повторное внедрение не выполняется, если патч уже присутствует.
     * </p>
     *
     * <p>
     * Безопасность: проверяется, что извлечённые файлы не выходят
     * за пределы целевой директории (защита от Zip Slip атаки).
     * Также ограничиваются количество записей в архиве и суммарный
     * размер распакованных файлов (защита от zip-бомб).
     * </p>
     *
     * @param zipPath   путь к ZIP-архиву.
     * @param targetDir целевая директория для извлечения.
     * @param files     список запрошенных файлов через запятую.
     * @param version   версия игры (для формирования патча).
     *
     * @throws IOException если произошла ошибка при чтении архива
     *         или записи файлов, а также при превышении лимитов распаковки
     * @throws RuntimeException если обнаружен некорректный путь в архиве
     *         (возможная Zip Slip атака)
     */
    private void extractNeededFiles(Path zipPath, Path targetDir, String files, GameVersion version) throws IOException {
        List<String> requested = Arrays.stream(files.split("\\s*,\\s*"))
                .map(s -> s.replaceAll("^/+", "").replaceAll("/+$", ""))
                .toList();

        long totalUnpacked = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {

            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {

                if (++entryCount > maxZipEntries) {
                    throw new IOException("В архиве слишком много файлов (больше " + maxZipEntries + ")");
                }

                String fullPath = entry.getName();

                // remove root folder
                String relativePath = fullPath.substring(fullPath.indexOf("/") + 1);

                if (relativePath.isBlank()) continue;

                boolean shouldExtract = requested.stream()
                        .anyMatch(req ->
                                relativePath.equals(req) ||
                                        relativePath.startsWith(req + "/")
                        );

                if (!shouldExtract) continue;

                Path normalizedTargetDir = targetDir.normalize();
                Path normalized = normalizedTargetDir.resolve(relativePath).normalize();

                if (!normalized.startsWith(normalizedTargetDir)) {
                    throw new RuntimeException("Invalid path in zip: " + relativePath);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(normalized);
                } else {
                    Files.createDirectories(normalized.getParent());

                    // размер записи в заголовке zip можно подделать, поэтому
                    // считаем реально распакованные байты
                    long remaining = maxUnpackedSize - totalUnpacked;

                    if (relativePath.endsWith("index.html")) {

                        ByteArrayOutputStream htmlBytes = new ByteArrayOutputStream();
                        totalUnpacked += copyLimited(zis, htmlBytes, remaining);

                        String html = htmlBytes.toString(java.nio.charset.StandardCharsets.UTF_8);

                        html = injectIntoHead(html, buildPatch(version));

                        Files.writeString(normalized, html, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    } else {
                        try (OutputStream out = Files.newOutputStream(normalized)) {
                            totalUnpacked += copyLimited(zis, out, remaining);
                        }
                    }
                }
            }
        }
    }

    /**
     * Копирует данные из потока, прерываясь при превышении лимита.
     *
     * @param in    входной поток (текущая запись zip-архива).
     * @param out   выходной поток.
     * @param limit максимально допустимое количество байт.
     *
     * @return количество скопированных байт.
     *
     * @throws IOException если данных больше, чем {@code limit}
     */
    private long copyLimited(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;

        while ((read = in.read(buffer)) != -1) {
            copied += read;

            if (copied > limit) {
                throw new IOException("Распакованные файлы игры превышают допустимый размер ("
                        + toMegabytes(maxUnpackedSize) + " МБ)");
            }

            out.write(buffer, 0, read);
        }

        return copied;
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static long toMegabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    /**
     * Внедряет JavaScript-код в секцию {@code <head>} HTML-документа.
     *
     * <p>
     * Если патч уже был внедрён ранее (определяется по наличию маркера
     * {@code __myng-storage-patch.js}), повторное внедрение не выполняется.
     * </p>
     *
     * @param html      исходный HTML-код.
     * @param injection JavaScript-код для внедрения.
     *
     * @return HTML с внедрённым кодом или исходный HTML,
     *         если патч уже присутствует.
     */
    private String injectIntoHead(String html, String injection) {
        if (html == null) return null;

        if (html.contains("__myng-storage-patch.js")) {
            return html;
        }

        return html.replaceFirst("(?i)<head>", "<head>\n" + injection);
    }

    /**
     * Формирует JavaScript-патч с информацией об игре и версии.
     *
     * <p>
     * Патч содержит:
     * </p>
     * <ul>
     *   <li>глобальные переменные с ID игры и версии;</li>
     *   <li>подключение скрипта {@code __myng-storage-patch.js}
     *       для взаимодействия с API платформы.</li>
     * </ul>
     *
     * @param version версия игры. Не должна быть null.
     *
     * @return строка с JavaScript-кодом для внедрения в HTML.
     */
    private String buildPatch(GameVersion version) {
        return "<script>window.__GAME_ID__=" + version.getGame().getId() + "; window.__GAMEVER_ID__=" + version.getId() + "</script>\n"
                + "<script src=\"/__myng-storage-patch.js\"></script>";
    }

    /**
     * Fetches the repository tree from GitHub using the configured WebClient.
     *
     * @param owner  The GitHub username or organization.
     * @param repo   The repository name.
     * @param commit The commit SHA or branch name.
     * @return A Mono containing the raw JSON response as a String.
     */
    public Mono<String> getRepositoryTree(String owner, String repo, String commit) {
        return this.webClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/{commit}?recursive=1", owner, repo, commit)
                .retrieve()
                .bodyToMono(String.class);
    }
}