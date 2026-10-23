package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.vlsu.myng.dto.MyGame;
import ru.vlsu.myng.dto.PublishGameRequest;
import ru.vlsu.myng.entities.*;
import ru.vlsu.myng.repositories.*;
import ru.vlsu.myng.dto.CatalogGameDTO;
import ru.vlsu.myng.dto.GameEditRequestDTO;
import ru.vlsu.myng.dto.GameFilterDTO;
import ru.vlsu.myng.dto.GamePageDTO;
import ru.vlsu.myng.utils.NotFoundException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления играми на платформе.<br>
 * <br>
 * Обеспечивает полный жизненный цикл игры:<br>
 * - публикация новой игры разработчиком с валидацией репозитория;<br>
 * - фильтрация и поиск игр в каталоге с пагинацией и сортировкой;<br>
 * - получение детальной информации об игре для страницы игры;<br>
 * - обновление метаданных игры (название, описание, изображение);<br>
 * - отслеживание статистики просмотров и запусков;<br>
 * - формирование выборок: популярные, новинки, лучшие за месяц.<br>
 * <br>
 * Используется в следующих сценариях:<br>
 * - разработчик публикует новую игру или новую версию;<br>
 * - пользователь просматривает каталог игр с фильтрами;<br>
 * - пользователь открывает страницу конкретной игры;<br>
 * - администратор или разработчик редактирует информацию об игре;<br>
 * - система формирует подборки для главной страницы.<br>
 * <br>
 * При публикации игры выполняется валидация GitHub-репозитория,
 * коммита и списка файлов через {@link GithubService}.
 * После сохранения создаётся связанная {@link GameVersion}
 * и {@link ModerationVerdict} для модерации.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final ReviewRepository reviewRepository;
    private final GameVersionRepository gameVersionRepository;
    private final ModerationVerdictRepository moderationVerdictRepository;
    private final UserRepository userRepository;
    private final GithubService githubService;
    private final UserService userService;
    private final TagService tagService;
    private final TagRepository tagRepository;

    /**
     * Возвращает список всех игр указанного разработчика.
     *
     * @param userId идентификатор пользователя-разработчика. Не должен быть null.
     *
     * @return список игр разработчика. Никогда не возвращает null.
     *         Может быть пустым, если разработчик ещё не опубликовал ни одной игры.
     *
     * @throws IllegalArgumentException                    если разработчик с
     *                                                     указанным id не найден
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<Game> getDeveloperGames(Integer userId) {
        User developer = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Developer not found"));

        return gameRepository.findByDeveloper(developer);
    }

    /**
     * Находит игру по уникальной ссылке на репозиторий.
     *
     * @param repo URL GitHub-репозитория. Не должен быть null или пустым.
     *
     * @return игра с указанным репозиторием.
     *
     * @throws IllegalArgumentException                    если игра с указанным
     *                                                     репозиторием не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Game getGameByRepo(String repo) {
        return gameRepository.findByRepo(repo)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));
    }

    /**
     * Возвращает список игр указанного жанра.
     *
     * @param genre жанр игры. Не должен быть null.
     *
     * @return список игр данного жанра. Никогда не возвращает null.
     *         Может быть пустым, если игр с таким жанром нет.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<Game> getGamesByGenre(Game.Genre genre) {
        return gameRepository.findByGenre(genre);
    }

    /**
     * Проверяет, существует ли игра с указанным репозиторием.
     *
     * @param repo URL GitHub-репозитория. Не должен быть null или пустым.
     *
     * @return true если игра с таким репозиторием уже зарегистрирована.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public boolean repoExists(String repo) {
        return gameRepository.existsByRepo(repo);
    }

    /**
     * Сохраняет или обновляет игру в базе данных.
     *
     * @param game игра для сохранения. Не должна быть null.
     *
     * @return сохранённая игра с актуальными данными.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Game save(Game game) {
        return gameRepository.save(game);
    }

    /**
     * Возвращает самую запускаемую игру на платформе.
     *
     * <p>
     * Игра с наибольшим количеством запусков за всё время.
     * Учитываются только игры, имеющие хотя бы одну
     * подтверждённую модерацией версию.
     * </p>
     *
     * @return DTO самой популярной игры или null,
     *         если на платформе нет опубликованных игр.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public CatalogGameDTO getMostLaunchedGame() {
        List<Game> games = gameRepository.findTopGamesByLaunches(PageRequest.of(0, 1));
        return games.isEmpty() ? null : convertToCatalogDto(games.get(0));
    }

    /**
     * Возвращает самую новую игру на платформе.
     *
     * <p>
     * Игра с самой последней подтверждённой версией.
     * Учитываются только игры, имеющие хотя бы одну
     * подтверждённую модерацией версию.
     * </p>
     *
     * @return DTO самой новой игры или null,
     *         если на платформе нет опубликованных игр.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public CatalogGameDTO getNewestGame() {
        var latestVersion = gameVersionRepository.findLatestApproved(PageRequest.of(0, 1))
                .stream().findFirst();
        if (latestVersion.isEmpty())
            return null;
        return convertToCatalogDto(latestVersion.get().getGame());
    }

    /**
     * Возвращает игру с наивысшим рейтингом за последние 30 дней.
     *
     * <p>
     * Если за последний месяц ни одна игра не получила оценок,
     * возвращается игра с наивысшим рейтингом за всё время.
     * Учитываются только игры с подтверждёнными версиями.
     * </p>
     *
     * @return DTO лучшей игры за месяц или null,
     *         если на платформе нет игр с рейтингом.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public CatalogGameDTO getTopRatingGameMonth() {
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Game> games = reviewRepository.findTopRatedGamesSince(monthAgo, PageRequest.of(0, 1));

        if (games.isEmpty()) {
            List<Game> allTimeBest = reviewRepository.findTopRatedGames(PageRequest.of(0, 1));
            return allTimeBest.isEmpty() ? null : convertToCatalogDto(allTimeBest.get(0));
        }

        return convertToCatalogDto(games.get(0));
    }

    /**
     * Возвращает список популярных игр для мини-каталога.
     *
     * <p>
     * Популярность определяется по количеству запусков
     * (сортировка "popular").
     * </p>
     *
     * @param limit максимальное количество возвращаемых игр. Должен быть > 0.
     *
     * @return список DTO популярных игр. Никогда не возвращает null.
     *         Может быть пустым, если на платформе нет игр.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public List<CatalogGameDTO> getPopularGames(int limit) {
        GameFilterDTO filter = new GameFilterDTO();
        filter.setSearch(null);
        filter.setTags(null);
        filter.setGenre(null);
        filter.setMinRating(null);
        filter.setSort("popular");

        var catalogGames = getFilteredGames(filter, PageRequest.of(0, limit));
        return catalogGames.getContent();
    }

    /**
     * Возвращает все игры для отображения в каталоге.
     *
     * <p>
     * Использует {@code findAllForCatalog} с EntityGraph
     * для предотвращения проблемы N+1 запросов.
     * </p>
     *
     * @return список DTO всех игр каталога. Никогда не возвращает null.
     *         Может быть пустым, если игры отсутствуют.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public List<CatalogGameDTO> getAllGamesForCatalog() {
        List<Game> games = gameRepository.findAllForCatalog();

        return games.stream()
                .map(this::convertToCatalogDto)
                .collect(Collectors.toList());
    }

    /**
     * Находит игру по идентификатору.
     *
     * @param id идентификатор игры. Не должен быть null.
     *
     * @return игра с указанным id.
     *
     * @throws RuntimeException                            если игра с указанным id
     *                                                     не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public Game getGameById(Integer id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Игра не найдена"));
    }

    /**
     * Преобразует сущность {@link Game} в DTO для каталога.
     *
     * <p>
     * Включает преобразование изображения в Base64
     * и назначение цвета жанра для UI.
     * </p>
     *
     * @param game игра для преобразования. Не должна быть null.
     *
     * @return DTO игры для каталога.
     */
    private CatalogGameDTO convertToCatalogDto(Game game) {
        Double avgRating = game.getAverageRating();
        Integer reviewsCount = game.getReviewCount();
        Integer totalViews = game.getTotalViews();
        Integer totalLaunches = game.getTotalLaunches();
        Instant firstReleaseDate = game.getFirstReleaseDate();

        String genreName = game.getGenre() != null ? game.getGenre().name() : "";

        Set<String> tags = game.getTags().stream()
                .map(Tag::getName)
                .filter(tagName -> !tagName.equalsIgnoreCase(genreName))
                .collect(Collectors.toSet());

        CatalogGameDTO dto = CatalogGameDTO.builder()
                .id(game.getId())
                .name(game.getName())
                .description(game.getDescr())
                .genre(game.getGenre())
                .developerUsername(game.getDeveloper() != null ? game.getDeveloper().getUsername() : "Unknown")
                .tags(tags)
                .averageRating(avgRating != null ? avgRating : 0.0)
                .reviewsCount(reviewsCount != null ? reviewsCount : 0)
                .totalViews(totalViews != null ? totalViews : 0)
                .totalLaunches(totalLaunches != null ? totalLaunches : 0)
                .firstReleaseDate(firstReleaseDate)
                .themeColor(getGenreColor(game.getGenre()))
                .build();

        if (game.getImage() != null) {
            String base64 = Base64.getEncoder().encodeToString(game.getImage());
            dto.setBase64Image("data:image/jpeg;base64," + base64);
        } else {
            dto.setBase64Image(null);
        }

        return dto;
    }

    /**
     * Возвращает CSS-класс градиента для указанного жанра.
     *
     * <p>
     * Каждый жанр имеет уникальную цветовую схему
     * для визуального различения в каталоге.
     * </p>
     *
     * @param genre жанр игры. Может быть null.
     *
     * @return строка с CSS-классами Tailwind.
     */
    private String getGenreColor(Game.Genre genre) {
        if (genre == null)
            return "from-blue-500 to-cyan-600";
        switch (genre) {
            case action:
                return "from-red-500 to-orange-600";
            case adventure:
                return "from-yellow-500 to-orange-600";
            case rpg:
                return "from-purple-500 to-pink-600";
            case simulation:
                return "from-green-500 to-emerald-600";
            case strategy:
                return "from-blue-500 to-cyan-600";
            case sports:
                return "from-green-500 to-blue-600";
            case puzzle:
                return "from-yellow-500 to-red-600";
            case horror:
                return "from-red-500 to-gray-600";
            case platformer:
                return "from-cyan-500 to-blue-600";
            case sandbox:
                return "from-yellow-500 to-red-600";
            case visual_novel:
                return "from-blue-500 to-pink-600";
            case roguelike:
                return "from-orange-500 to-red-600";
            default:
                return "from-blue-500 to-cyan-600";
        }
    }

    /**
     * Возвращает страницу игр каталога с фильтрацией и пагинацией.
     *
     * <p>
     * Поддерживаемые фильтры:
     * </p>
     * <ul>
     * <li>поиск по названию, описанию и разработчику;</li>
     * <li>жанр игры;</li>
     * <li>теги;</li>
     * <li>минимальный рейтинг.</li>
     * </ul>
     *
     * <p>
     * Поддерживаемые сортировки:
     * </p>
     * <ul>
     * <li>newest — сначала новые (по дате первого релиза);</li>
     * <li>oldest — сначала старые;</li>
     * <li>rating_high — по убыванию рейтинга;</li>
     * <li>rating_low — по возрастанию рейтинга;</li>
     * <li>popular — по убыванию количества запусков.</li>
     * </ul>
     *
     * @param filter   DTO с параметрами фильтрации. Может быть с null-полями.
     * @param pageable параметры пагинации. Не должен быть null.
     *
     * @return страница DTO игр, удовлетворяющих фильтрам.
     *
     * @throws IllegalArgumentException                    если pageable равен null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public Page<CatalogGameDTO> getFilteredGames(
            GameFilterDTO filter,
            Pageable pageable) {

        Sort sort = buildAggregateSort(filter.getSort());

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort);

        Page<CatalogGameDTO> page = gameRepository.findCatalogGames(
                filter.getSearch(),
                filter.getGenre(),
                filter.getTags(),
                filter.getMinRating(),
                sortedPageable);

        page.getContent().forEach(dto -> {

            dto.setThemeColor(
                    getGenreColor(dto.getGenre()));

            dto.setTags(
                    tagRepository.findTagNamesByGameId(dto.getId()));
        });

        return page;
    }

    /**
     * Преобразует строковый параметр сортировки в объект {@link Sort}
     * для использования в запросах к базе данных.
     *
     * <p>
     * Если параметр sort равен null, используется сортировка "newest"
     * (сначала новые игры).
     * </p>
     *
     * @param sort строковый идентификатор сортировки. Может быть null.
     *
     * @return объект Sort для JPA-запроса.
     */
    private Sort buildAggregateSort(String sort) {

        if (sort == null)
            sort = "newest";

        return switch (sort) {

            case "oldest" ->
                JpaSort.unsafe(
                        Sort.Direction.ASC,
                        "g.firstReleaseDate");

            case "rating_high" ->
                JpaSort.unsafe(
                        Sort.Direction.DESC,
                        "g.averageRating");

            case "rating_low" ->
                JpaSort.unsafe(
                        Sort.Direction.ASC,
                        "g.averageRating");

            case "popular" ->
                JpaSort.unsafe(
                        Sort.Direction.DESC,
                        "g.totalLaunches");

            default ->
                JpaSort.unsafe(
                        Sort.Direction.DESC,
                        "g.firstReleaseDate");
        };
    }

    /**
     * Возвращает полные данные игры для отображения на странице игры.
     *
     * <p>
     * Включает:
     * </p>
     * <ul>
     * <li>основную информацию об игре;</li>
     * <li>список подтверждённых версий (от новых к старым);</li>
     * <li>последнюю подтверждённую версию;</li>
     * <li>последние отзывы (до 5);</li>
     * <li>статистику просмотров и запусков;</li>
     * <li>изображение игры в формате Base64.</li>
     * </ul>
     *
     * @param gameId идентификатор игры. Не должен быть null.
     *
     * @return DTO с полными данными игры.
     *
     * @throws RuntimeException                            если игра с указанным id
     *                                                     не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional(readOnly = true)
    public GamePageDTO getGamePageData(Integer gameId) {

        Game game = getGameById(gameId);

        Double avgRating = game.getAverageRating();
        Integer reviewsCount = game.getReviewCount();
        Integer totalViews = game.getTotalViews();
        Integer totalLaunches = game.getTotalLaunches();

        Instant firstReleaseDate = game.getFirstReleaseDate();

        List<GameVersion> versions = game.getVersions().stream()
                .filter(version -> version.getModerationVerdict() != null &&
                        Boolean.TRUE.equals(
                                version.getModerationVerdict().getApproved()))
                .sorted(Comparator.comparing(GameVersion::getCreatedAt).reversed())
                .collect(Collectors.toList());

        GameVersion latestVersion = versions.isEmpty() ? null : versions.get(0);

        List<Review> recentReviews = reviewRepository.findRecentReviews(
                game.getId(),
                9,
                PageRequest.of(0, 5));

        Instant lastUpdateDate = latestVersion != null ? latestVersion.getCreatedAt() : null;

        String themeColor = getGenreColor(game.getGenre());

        GamePageDTO dto = GamePageDTO.builder()
                .id(game.getId())
                .name(game.getName())
                .description(game.getDescr())
                .repoLink(game.getRepo())
                .themeColor(themeColor)
                .genre(game.getGenre())
                .developer(game.getDeveloper())
                .tags(game.getTags())
                .averageRating(avgRating != null ? avgRating : 0.0)
                .totalViews(totalViews != null ? totalViews : 0)
                .totalLaunches(totalLaunches != null ? totalLaunches : 0)
                .reviewsCount(reviewsCount != null ? reviewsCount : 0)
                .versions(versions)
                .latestVersion(latestVersion)
                .recentReviews(recentReviews)
                .firstReleaseDate(firstReleaseDate)
                .lastUpdateDate(lastUpdateDate)
                .build();

        if (game.getImage() != null && game.getImage().length > 0) {
            String base64 = Base64.getEncoder().encodeToString(game.getImage());
            dto.setBase64Image("data:image/jpeg;base64," + base64);
        } else {
            dto.setBase64Image(null);
        }

        return dto;
    }

    /**
     * Обновляет метаданные игры.
     *
     * <p>
     * Можно обновить название, описание и изображение.
     * Если поле в запросе равно null или пустой строке,
     * соответствующее поле игры не изменяется.
     * </p>
     *
     * @param gameId  идентификатор игры. Не должен быть null.
     * @param request DTO с новыми данными. Не должен быть null.
     *
     * @throws RuntimeException                            если игра с указанным id
     *                                                     не найдена
     * @throws IOException                                 если не удалось прочитать
     *                                                     байты изображения
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void updateGame(Integer gameId, GameEditRequestDTO request) throws IOException {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Игра не найдена"));

        if (request.getName() != null && !request.getName().isBlank()) {
            game.setName(request.getName());
        }
        game.setDescr(request.getDescription());

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            game.setImage(request.getImage().getBytes());
        }

        gameRepository.save(game);
    }

    /**
     * Возвращает список игр указанного пользователя с дополнительной
     * информацией для личного кабинета разработчика.
     *
     * <p>
     * Для каждой игры определяется:
     * </p>
     * <ul>
     * <li>статус модерации последней версии (опубликована/отклонена/на
     * модерации);</li>
     * <li>общее количество просмотров;</li>
     * <li>средний рейтинг;</li>
     * <li>изображение в формате Base64.</li>
     * </ul>
     *
     * @param user пользователь-разработчик. Не должен быть null.
     *
     * @return список DTO игр пользователя. Никогда не возвращает null.
     *         Может быть пустым, если пользователь ещё не публиковал игры.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<MyGame> getGamesForUser(User user) {
        List<Game> games = gameRepository.findByDeveloper(user);

        return games.stream().map(game -> {
            GameVersion latestVersion = game.getVersions().stream()
                    .max(Comparator.comparing(GameVersion::getCreatedAt))
                    .orElse(null);

            Boolean approved = null;
            if (latestVersion != null) {
                ModerationVerdict verdict = latestVersion.getModerationVerdict();
                approved = verdict.getApproved();
            }

            String status;
            if (approved != null) {
                status = approved ? "Опубликована" : "Отклонена";
            } else {
                status = "На модерации";
            }

            int views = game.getStats().stream()
                    .filter(s -> s.getEventType() == GameStats.EventType.view)
                    .mapToInt(GameStats::getCount)
                    .sum();

            double rating = game.getReviews().isEmpty() ? 0.0
                    : game.getReviews().stream()
                            .mapToDouble(Review::getRating)
                            .average()
                            .orElse(0.0);

            String imageSrc = null;

            if (game.getImage() != null && game.getImage().length > 0) {

                String base64 = Base64.getEncoder()
                        .encodeToString(game.getImage());

                imageSrc = "data:image/jpeg;base64," + base64;
            }

            String bgClass = getGenreColor(game.getGenre());

            return new MyGame(game.getId(), game.getName(), game.getDescr(), approved, status, views, rating, imageSrc,
                    bgClass);
        }).collect(Collectors.toList());
    }

    /**
     * Публикует новую игру на платформе.
     *
     * <p>
     * Процесс публикации включает:
     * </p>
     * <ol>
     * <li>Валидацию GitHub-репозитория через {@link GithubService};</li>
     * <li>Валидацию существования коммита;</li>
     * <li>Валидацию наличия указанных файлов в коммите;</li>
     * <li>Создание сущности {@link Game} с метаданными и изображением;</li>
     * <li>Привязку тегов (существующих или новых);</li>
     * <li>Создание {@link GameVersion} с информацией о коммите;</li>
     * <li>Создание {@link ModerationVerdict} для отправки на модерацию.</li>
     * </ol>
     *
     * <p>
     * Требования к входным данным:
     * </p>
     * <ul>
     * <li>repoLink — URL формата https://github.com/[username]/[repo];</li>
     * <li>gameVer — строка версии формата v[число].[число]...;</li>
     * <li>commitHash — 7-значный хеш коммита;</li>
     * <li>title — от 3 до 100 символов;</li>
     * <li>description — от 10 до 2000 символов;</li>
     * <li>mainPic — изображение размером не более 32 МБ;</li>
     * <li>tags — строка тегов формата "#tag1, #tag2";</li>
     * <li>files — список имён файлов через запятую.</li>
     * </ul>
     *
     * @param dto запрос на публикацию игры. Не должен быть null.
     *
     * @throws RuntimeException                            если валидация GitHub не
     *                                                     пройдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public void publishGame(PublishGameRequest dto) {
        githubService.validateRepoExists(dto.getRepoLink());
        githubService.validateCommitExists(dto.getRepoLink(), dto.getCommitHash());
        githubService.validateFilesExistInCommit(dto.getRepoLink(), dto.getCommitHash(), dto.getFiles());

        Game game = new Game();

        game.setName(dto.getTitle());
        game.setDescr(dto.getDescription());
        game.setRepo(dto.getRepoLink());
        try {
            var file = dto.getMainPic();
            if (file != null && !file.isEmpty() && file.getSize() > 0) {
                game.setImage(file.getBytes());
            } else {
                game.setImage(null);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        game.setGenre(
                Game.Genre.valueOf(dto.getGenre()));

        User currentUser = userService.getCurrentUser();
        game.setDeveloper(currentUser);

        Set<Tag> tags = Arrays.stream(dto.getTags().split(","))
                .map(String::trim)
                .map(tag -> tag.startsWith("#") ? tag.substring(1) : tag)
                .map(String::toLowerCase)
                .map(tagName -> tagService.findOrCreate(tagName))
                .collect(Collectors.toSet());

        game.setTags(tags);

        game.setFirstReleaseDate(Instant.now());

        gameRepository.save(game);

        System.out.println("publishing game with files: " + dto.getFiles());

        GameVersion version = new GameVersion();
        version.setGame(game);
        version.setCommitHash(dto.getCommitHash());
        version.setName(dto.getGameVer());
        version.setCreatedAt(game.getFirstReleaseDate());
        version.setFiles(dto.getFiles());
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
     * Увеличивает счётчик запусков игры на 1.
     *
     * @param game игра, для которой увеличивается счётчик. Не должна быть null.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void incrementGameTotalLaunches(Game game) {
        game.setTotalLaunches(game.getTotalLaunches() + 1);
        gameRepository.save(game);
    }

    /**
     * Увеличивает счётчик запусков игры на 1 по её идентификатору.
     *
     * @param gameId идентификатор игры. Не должен быть null.
     *
     * @throws RuntimeException                            если игра с указанным id
     *                                                     не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void incrementGameTotalLaunches(Integer gameId) {
        var game = getGameById(gameId);
        game.setTotalLaunches(game.getTotalLaunches() + 1);
        gameRepository.save(game);
    }

    /**
     * Увеличивает счётчик просмотров игры на 1.
     *
     * @param game игра, для которой увеличивается счётчик. Не должна быть null.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void incrementGameTotalViews(Game game) {
        game.setTotalViews(game.getTotalViews() + 1);
        gameRepository.save(game);
    }

    /**
     * Увеличивает счётчик просмотров игры на 1 по её идентификатору.
     *
     * @param gameId идентификатор игры. Не должен быть null.
     *
     * @throws RuntimeException                            если игра с указанным id
     *                                                     не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void incrementGameTotalViews(Integer gameId) {
        var game = getGameById(gameId);
        game.setTotalViews(game.getTotalViews() + 1);
        gameRepository.save(game);
    }
}