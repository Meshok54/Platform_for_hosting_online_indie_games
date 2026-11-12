package ru.vlsu.myng.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.myng.entities.GameVersion;
import ru.vlsu.myng.entities.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Компонент слоя доступа к данным для работы с сущностью GameVersion.<br>
 * <br>
 * Обеспечивает операции сохранения, удаления и поиска версий игр.<br>
 * Используется в следующих сценариях:<br>
 * - добавление новой версии игры разработчиком;<br>
 * - получение всех версий конкретной игры;<br>
 * - поиск версии по commit hash для модерации или отката;<br>
 * - подготовка списка версий для отображения в UI;<br>
 * - проверка существования версии по commit hash.<br>
 * <br>
 * Наследует стандартные CRUD-операции из JpaRepository.
 */
public interface GameVersionRepository extends JpaRepository<GameVersion, Integer> {

    /**
     * Возвращает список всех версий указанной игры.
     *
     * @param game игра, для которой ищутся версии.
     *             Не должен быть null.
     *             Должен быть персистентной сущностью (id != null).
     *
     * @return список версий игры. Никогда не возвращает null.
     *         Может быть пустым, если у игры ещё нет версий.
     *
     * @throws IllegalArgumentException                    если game равна null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к базе
     *                                                     данных
     */
    List<GameVersion> findByGame(Game game);

    /**
     * Поиск версии игры по commit hash.
     *
     * @param commitHash хеш коммита. Не должен быть null или пустым.
     *
     * @return Optional с версией игры.
     *         Optional.empty() если версия с указанным commit hash не найдена.
     *
     * @throws IllegalArgumentException                    если commitHash равен
     *                                                     null или пустая строка
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к базе
     *                                                     данных
     */
    Optional<GameVersion> findByCommitHash(String commitHash);

    /**
     * Возвращает первую созданную версию указанной игры.
     *
     * <p>
     * Версия определяется по минимальному значению createdAt.
     * </p>
     *
     * @param game игра, для которой выполняется поиск версии.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @return Optional с самой ранней версией игры.
     *         Возвращает Optional.empty(),
     *         если у игры отсутствуют версии.
     *
     * @throws IllegalArgumentException                    если game равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    Optional<GameVersion> findFirstByGameOrderByCreatedAtAsc(Game game);


    /**
     * Возвращает последнюю созданную версию игры.
     *
     * <p>
     * Версия определяется по максимальному значению createdAt.
     * </p>
     *
     * @return последняя созданная версия игры.
     *         Может возвращать null,
     *         если версии отсутствуют.
     *
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    GameVersion findFirstByOrderByCreatedAtDesc();


    /**
     * Возвращает список версий игры,
     * отсортированных по дате создания в порядке возрастания.
     *
     * @param gameId идентификатор игры.
     *               Не должен быть null.
     *               Должен соответствовать существующей игре.
     *
     * @return список версий игры,
     *         отсортированный от самой ранней версии
     *         к самой новой.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если у игры отсутствуют версии.
     *
     * @throws IllegalArgumentException                    если gameId равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    List<GameVersion> findByGameIdOrderByCreatedAtAsc(Integer gameId);


    /**
     * Возвращает список последних подтверждённых версий игр.
     *
     * <p>
     * В результат включаются только версии,
     * прошедшие модерацию и имеющие approved = true.
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
     * @param pageable параметры пагинации,
     *                 определяющие количество возвращаемых записей.
     *                 Не должен быть null.
     *
     * @return список последних подтверждённых версий игр.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если подтверждённые версии отсутствуют.
     *
     * @throws IllegalArgumentException                    если pageable равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("""
        SELECT gv
        FROM GameVersion gv
        WHERE gv.moderationVerdict IS NOT NULL
        AND gv.moderationVerdict.approved = true
        ORDER BY gv.createdAt DESC
        """)
    List<GameVersion> findLatestApproved(Pageable pageable);

    /**
     * Проверяет, есть ли у игры хотя бы одна версия, одобренная модератором.
     * Только такие игры считаются опубликованными и видны игрокам.
     *
     * @param gameId идентификатор игры.
     *
     * @return true, если у игры есть одобренная версия.
     */
    @Query("""
            SELECT COUNT(gv) > 0
            FROM GameVersion gv
            WHERE gv.game.id = :gameId
            AND gv.moderationVerdict.approved = true
            """)
    boolean existsApprovedByGameId(@Param("gameId") Integer gameId);
}