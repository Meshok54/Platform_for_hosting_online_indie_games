package ru.vlsu.myng.repositories;

import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.User;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Компонент слоя доступа к данным для работы с сущностью Review.<br>
 * <br>
 * Обеспечивает операции сохранения, удаления и поиска отзывов
 * пользователей.<br>
 * Используется в следующих сценариях:<br>
 * - пользователь оставляет отзыв на игру;<br>
 * - проверка наличия существующего отзыва пользователя для игры;<br>
 * - отображение отзывов игры на странице игры;<br>
 * - аналитика по рейтингам и количеству отзывов;<br>
 * <br>
 * Наследует стандартные CRUD-операции из JpaRepository и поддерживает
 * динамические предикаты через JpaSpecificationExecutor.
 */
public interface ReviewRepository extends JpaRepository<Review, Integer>, JpaSpecificationExecutor<Review> {

    /**
     * Возвращает список всех отзывов для указанной игры.
     *
     * @param game игра. Не должна быть null.
     *
     * @return список отзывов игры. Никогда не возвращает null.
     *         Может быть пустым, если отзывов ещё нет.
     *
     * @throws IllegalArgumentException                    если game равна null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    List<Review> findByGame(Game game);

    /**
     * Возвращает отзыв пользователя для конкретной игры, если он существует.
     *
     * @param user пользователь. Не должен быть null.
     * @param game игра. Не должна быть null.
     *
     * @return Optional с отзывом. Optional.empty(), если отзыв отсутствует.
     *
     * @throws IllegalArgumentException                    если user или game равны
     *                                                     null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    Optional<Review> findByUserAndGame(User user, Game game);

    /**
     * Проверяет, существует ли отзыв пользователя для игры.
     *
     * @param user пользователь. Не должен быть null.
     * @param game игра. Не должна быть null.
     *
     * @return true, если отзыв существует, иначе false.
     *
     * @throws IllegalArgumentException                    если user или game равны
     *                                                     null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    boolean existsByUserAndGame(User user, Game game);

    /**
     * Возвращает список отзывов указанного пользователя.
     *
     * @param user пользователь. Не должен быть null.
     *
     * @return список отзывов пользователя. Никогда не возвращает null.
     *         Может быть пустым, если отзывов нет.
     *
     * @throws IllegalArgumentException                    если user равен null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    List<Review> findByUser(User user);

    /**
     * Возвращает средний рейтинг указанной игры.
     *
     * @param game игра, для которой вычисляется средний рейтинг.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @return средний рейтинг игры.
     *         Может возвращать null,
     *         если у игры отсутствуют отзывы.
     *
     * @throws IllegalArgumentException                    если game равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.game = :game")
    Double getAverageRatingByGame(@Param("game") Game game);

    /**
     * Возвращает количество отзывов для указанной игры.
     *
     * @param game игра, для которой подсчитываются отзывы.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @return количество отзывов игры.
     *         Никогда не возвращает null.
     *         Возвращает 0, если отзывы отсутствуют.
     *
     * @throws IllegalArgumentException                    если game равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.game = :game")
    Integer countByGame(@Param("game") Game game);

    /**
     * Возвращает список игр с наивысшим средним рейтингом
     * за указанный период времени.
     *
     * <p>
     * В расчёт включаются только отзывы,
     * созданные после даты since.
     * </p>
     *
     * <p>
     * В результат включаются только игры,
     * имеющие хотя бы одну подтверждённую модерацией версию.
     * </p>
     *
     * <p>
     * Список сортируется по среднему рейтингу
     * в порядке убывания.
     * </p>
     *
     * @param since дата начала периода.
     *              Не должна быть null.
     *
     * @param pageable параметры пагинации,
     *                 определяющие количество возвращаемых записей.
     *                 Не должен быть null.
     *
     * @return список игр с наивысшим рейтингом за указанный период.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если подходящие игры отсутствуют.
     *
     * @throws IllegalArgumentException                    если since
     *                                                     или pageable равны null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("""
            SELECT r.game
            FROM Review r
            WHERE r.createdAt > :since
            AND EXISTS (
                SELECT 1
                FROM GameVersion gv
                WHERE gv.game = r.game
                AND gv.moderationVerdict IS NOT NULL
                AND gv.moderationVerdict.approved = true
            )
            GROUP BY r.game
            ORDER BY AVG(r.rating) DESC
            """)
    List<Game> findTopRatedGamesSince(Instant since, PageRequest pageable);


    /**
     * Возвращает список игр с наивысшим средним рейтингом.
     *
     * <p>
     * В результат включаются только игры,
     * имеющие хотя бы одну подтверждённую модерацией версию.
     * </p>
     *
     * <p>
     * Список сортируется по среднему рейтингу
     * в порядке убывания.
     * </p>
     *
     * @param pageable параметры пагинации,
     *                 определяющие количество возвращаемых записей.
     *                 Не должен быть null.
     *
     * @return список игр с наивысшим рейтингом.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если подходящие игры отсутствуют.
     *
     * @throws IllegalArgumentException                    если pageable равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("""
            SELECT g
            FROM Game g
            WHERE EXISTS (
                SELECT 1
                FROM GameVersion gv
                WHERE gv.game = g
                AND gv.moderationVerdict IS NOT NULL
                AND gv.moderationVerdict.approved = true
            )
            ORDER BY g.averageRating DESC
            """)
    List<Game> findTopRatedGames(PageRequest pageable);

    /**
     * Возвращает список отзывов указанной игры,
     * отсортированных по дате создания в порядке убывания.
     *
     * <p>
     * Сначала возвращаются самые новые отзывы.
     * </p>
     *
     * <p>
     * Количество возвращаемых записей определяется
     * параметрами pageable.
     * </p>
     *
     * @param game игра, для которой загружаются отзывы.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @param pageable параметры пагинации,
     *                 определяющие количество возвращаемых записей.
     *                 Не должен быть null.
     *
     * @return список отзывов игры,
     *         отсортированный от новых к старым.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если отзывы отсутствуют.
     *
     * @throws IllegalArgumentException                    если game
     *                                                     или pageable равны null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    List<Review> findByGameOrderByCreatedAtDesc(Game game, PageRequest pageable);

    /**
     * Проверяет наличие отзыва пользователя для указанной игры.
     *
     * @param game игра, для которой выполняется проверка.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @param user пользователь — автор отзыва.
     *             Не должен быть null.
     *             Должен быть персистентной сущностью (id != null).
     *
     * @return true, если пользователь уже оставил отзыв на игру;
     *         false — если отзыв отсутствует.
     *
     * @throws IllegalArgumentException                    если game
     *                                                     или user равны null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    boolean existsByGameAndUser(Game game, User user);


    /**
     * Возвращает список последних отзывов игры
     * с ограничением по количеству жалоб.
     *
     * <p>
     * В результат включаются только отзывы,
     * у которых количество жалоб
     * меньше либо равно maxReports.
     * </p>
     *
     * <p>
     * Список сортируется по дате создания
     * в порядке убывания.
     * </p>
     *
     * <p>
     * Количество возвращаемых записей определяется
     * параметрами pageable.
     * </p>
     *
     * @param gameId идентификатор игры.
     *               Не должен быть null.
     *
     * @param maxReports максимальное допустимое количество жалоб.
     *
     * @param pageable параметры пагинации,
     *                 определяющие количество возвращаемых записей.
     *                 Не должен быть null.
     *
     * @return список последних отзывов игры,
     *         удовлетворяющих ограничению по жалобам.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если подходящие отзывы отсутствуют.
     *
     * @throws IllegalArgumentException                    если gameId
     *                                                     или pageable равны null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("""
            SELECT r FROM Review r
            WHERE r.game.id = :gameId
              AND r.reportCount <= :maxReports
            ORDER BY r.createdAt DESC
            """)
    List<Review> findRecentReviews(
            @Param("gameId") Integer gameId,
            @Param("maxReports") int maxReports,
            PageRequest pageable);
}