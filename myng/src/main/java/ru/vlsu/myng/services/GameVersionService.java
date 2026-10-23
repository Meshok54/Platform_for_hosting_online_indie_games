package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.myng.dto.PublishGameVersionRequest;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.GameVersion;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.GameRepository;
import ru.vlsu.myng.repositories.GameVersionRepository;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import ru.vlsu.myng.utils.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.stream.Stream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Сервис для управления версиями игр на платформе.<br>
 * <br>
 * Обеспечивает полный жизненный цикл версии игры:<br>
 * - публикация новой версии игры разработчиком с валидацией GitHub;<br>
 * - получение информации о версии по идентификатору;<br>
 * - определение точки входа (entry point) для запуска игры в браузере;<br>
 * - удаление версии с очисткой файлов на диске и обновлением метаданных игры.<br>
 * <br>
 * Используется в следующих сценариях:<br>
 * - разработчик публикует обновление для своей игры;<br>
 * - система определяет, какой файл запускать при открытии игры;<br>
 * - модератор или разработчик удаляет некачественную версию;<br>
 * - администратор очищает устаревшие версии игр.<br>
 * <br>
 * При публикации версии автоматически создаётся связанный
 * {@link ModerationVerdict} для отправки на модерацию.
 * Файлы версий хранятся в файловой системе по пути,
 * указанному в свойстве {@code app.storage.path}.
 */
@Service
@RequiredArgsConstructor
public class GameVersionService {

    private final GameVersionRepository gameVersionRepository;
    private final ModerationVerdictRepository moderationVerdictRepository;
    private final GameRepository gameRepository;
    private final GameService gameService;
    private final GithubService githubService;

    @Value("${app.storage.path}")
    private String storagePath;

    @Value("${server.port}")
    private String port;

    /**
     * Находит версию игры по идентификатору.
     *
     * @param id идентификатор версии игры. Не должен быть null.
     *
     * @return версия игры с указанным id.
     *
     * @throws RuntimeException если версия с указанным id не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public GameVersion getGameVersionById(Integer id) {
        return gameVersionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Версия игры не найдена"));
    }

    /**
     * Проверяет, что пользователь — разработчик указанной игры.
     *
     * @throws AccessDeniedException если игра принадлежит другому разработчику
     */
    private void checkIsDeveloper(Game game, User currentUser) {
        User developer = game.getDeveloper();

        if (developer == null || !developer.getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Вы не являетесь разработчиком этой игры");
        }
    }

    /**
     * Определяет URL для запуска игры в браузере.
     *
     * <p>
     * Ищет файл {@code index.html} в директории с файлами версии
     * и формирует HTTP-ссылку для доступа к нему.
     * </p>
     *
     * <p>
     * Структура пути к файлам версии:
     * </p>
     * <pre>
     * {storagePath}/gamefiles/game_{gameId}/ver_{versionId}/index.html
     * </pre>
     *
     * <p>
     * Результирующий URL имеет формат:
     * </p>
     * <pre>
     * http://localhost:{port}/static/gamefiles/game_{gameId}/ver_{versionId}/index.html
     * </pre>
     *
     * @param version версия игры. Не должна быть null.
     *
     * @return полный URL для запуска игры в браузере.
     *
     * @throws RuntimeException если:
     *         <ul>
     *           <li>файл index.html не найден в директории версии;</li>
     *           <li>произошла ошибка при обходе файловой системы.</li>
     *         </ul>
     */
    public String resolveEntryPoint(GameVersion version) {
        return "http://localhost:" + port + "/static/gamefiles/game_" + version.getGame().getId() + "/ver_" + version.getId() + "/" + version.getEntryPoint(); // the path is most likely fucked up
    }

    /**
     * Публикует новую версию игры на платформе.
     *
     * <p>
     * Процесс публикации включает:
     * </p>
     * <ol>
     *   <li>Поиск игры по идентификатору;</li>
     *   <li>Валидацию существования коммита в GitHub-репозитории;</li>
     *   <li>Валидацию наличия указанных файлов в коммите;</li>
     *   <li>Создание сущности {@link GameVersion} с метаданными;</li>
     *   <li>Создание {@link ModerationVerdict} для отправки на модерацию.</li>
     * </ol>
     *
     * <p>
     * Требования к входным данным:
     * </p>
     * <ul>
     *   <li>gameId — идентификатор существующей игры;</li>
     *   <li>gameVerName — имя версии (например, "v1.2.0");</li>
     *   <li>commitHash — хеш коммита в GitHub-репозитории;</li>
     *   <li>files — список имён файлов через запятую;</li>
     *   <li>changelog — описание изменений (опционально).</li>
     * </ul>
     *
     * <p>
     * Валидация выполняется через {@link GithubService}:
     * </p>
     * <ul>
     *   <li>проверка существования коммита в репозитории;</li>
     *   <li>проверка наличия всех указанных файлов в коммите.</li>
     * </ul>
     *
     * @param dto         запрос на публикацию версии игры. Не должен быть null.
     * @param currentUser пользователь, публикующий версию.
     *                    Должен быть разработчиком этой игры.
     *
     * @throws AccessDeniedException если игра принадлежит другому разработчику
     * @throws RuntimeException если:
     *         <ul>
     *           <li>игра с указанным id не найдена;</li>
     *           <li>валидация GitHub не пройдена.</li>
     *         </ul>
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public void publishGameVersion(PublishGameVersionRequest dto, User currentUser) {
        var game = gameService.getGameById(dto.getGameId());

        checkIsDeveloper(game, currentUser);

        githubService.validateCommitExists(game.getRepo(), dto.getCommitHash());
        githubService.validateFilesExistInCommit(game.getRepo(), dto.getCommitHash(), dto.getFiles());

        System.out.println("publishing gameversion with files: " + dto.getFiles());
        
        GameVersion version = new GameVersion();
        version.setGame(game);
        version.setCommitHash(dto.getCommitHash());
        version.setName(dto.getGameVerName());
        version.setCreatedAt(Instant.now());
        version.setFiles(dto.getFiles());
        version.setChangelog(dto.getChangelog());
        version.setEntryPoint(dto.getEntryPoint());

        gameVersionRepository.save(version);

        ModerationVerdict verdict = new ModerationVerdict();
        verdict.setGameVersion(version);
        verdict.setApproved(null);
        verdict.setReason(null);
        verdict.setModerator(null);

        moderationVerdictRepository.save(verdict);

        version.setModerationVerdict(verdict);
    }

    /**
     * Удаляет версию игры вместе с файлами на диске.
     *
     * <p>
     * Процесс удаления включает:
     * </p>
     * <ol>
     *   <li>Проверку принадлежности версии указанной игре;</li>
     *   <li>Удаление директории с файлами версии из файловой системы;</li>
     *   <li>Удаление связанного {@link ModerationVerdict} (если есть);</li>
     *   <li>Удаление записи о версии из базы данных;</li>
     *   <li>Обновление даты первого релиза игры:
     *     <ul>
     *       <li>если удалена единственная версия — дата сбрасывается в null;</li>
     *       <li>иначе — устанавливается дата самой старой из оставшихся версий.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>
     * Директория с файлами версии находится по пути:
     * </p>
     * <pre>
     * {storagePath}/gamefiles/game_{gameId}/ver_{versionId}/
     * </pre>
     *
     * <p>
     * Удаление директории выполняется рекурсивно:
     * сначала удаляются все файлы и поддиректории,
     * затем — сама директория версии.
     * </p>
     *
     * @param gameId      идентификатор игры. Не должен быть null.
     * @param versionId   идентификатор версии. Не должен быть null.
     * @param currentUser пользователь, удаляющий версию.
     *                    Должен быть разработчиком этой игры.
     *
     * @throws NotFoundException        если версия с указанным id не найдена
     * @throws IllegalArgumentException если версия не принадлежит указанной игре
     * @throws AccessDeniedException    если игра принадлежит другому разработчику
     * @throws RuntimeException если не удалось удалить файлы с диска
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void deleteGameVersion(Integer gameId, Integer versionId, User currentUser) {
        GameVersion version = gameVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Версия не найдена"));

        if (!version.getGame().getId().equals(gameId)) {
            throw new IllegalArgumentException("Версия не принадлежит данной игре");
        }

        checkIsDeveloper(version.getGame(), currentUser);

        Path versionPath = Paths.get("..", "storage", "gamefiles", "game_" + gameId, "ver_" + versionId);

        if (Files.exists(versionPath)) {
            try {
                Files.walk(versionPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("Папка с файлами версии успешно удалена: " + versionPath.toAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Не удалось удалить файлы версии с сервера: " + e.getMessage());
            }
        }

        if (version.getModerationVerdict() != null) {
            moderationVerdictRepository.delete(version.getModerationVerdict());
        }
        gameVersionRepository.delete(version);

        Game game = version.getGame();
        var remainingVersions = gameVersionRepository.findByGameIdOrderByCreatedAtAsc(gameId);

        if (remainingVersions.isEmpty()) {
            game.setFirstReleaseDate(null);
        } else {
            Instant oldestVersionDate = remainingVersions.get(0).getCreatedAt();
            game.setFirstReleaseDate(oldestVersionDate);
        }

        gameRepository.save(game);
    }
}