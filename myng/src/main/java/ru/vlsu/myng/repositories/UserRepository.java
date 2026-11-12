package ru.vlsu.myng.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.myng.entities.User;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

/**
 * Компонент слоя доступа к данным для работы с сущностью User.<br>
 * <br>
 * Обеспечивает операции сохранения, удаления и поиска пользователей.<br>
 * Используется в следующих сценариях:<br>
 * - регистрация новых пользователей;<br>
 * - аутентификация и авторизация;<br>
 * - поиск пользователей по username, email или GitHub username;<br>
 * - фильтрация пользователей по роли (user, dev, mod, admin);<br>
 * - проверка уникальности username и email перед созданием нового
 * пользователя;<br>
 * - подготовка списков пользователей для аналитики и отображения в UI.<br>
 * <br>
 * Наследует стандартные CRUD-операции из JpaRepository.
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Поиск пользователя по username.
     *
     * @param username имя пользователя. Не должно быть null или пустым.
     *
     * @return Optional с найденным пользователем. Optional.empty(), если
     *         пользователь не найден.
     *
     * @throws IllegalArgumentException                    если username равен null
     *                                                     или пустой строке
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    Optional<User> findByUsername(String username);

    /**
     * Поиск пользователя по email.
     * <p>
     * <strong>Важно:</strong> Для корректной работы этого метода необходимо
     * добавить
     * поле {@code email} в сущность {@link User} и соответствующую колонку в
     * таблицу БД.
     * </p>
     *
     * @param email email пользователя. Не должен быть null.
     *
     * @return Optional с найденным пользователем. Optional.empty(), если
     *         пользователь не найден.
     *
     * @throws IllegalArgumentException                    если email равен null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    Optional<User> findByEmail(String email);

    /**
     * Поиск пользователя по GitHub username.
     *
     * @param githubUsername GitHub username. Может быть null.
     *
     * @return Optional с найденным пользователем. Optional.empty(), если
     *         пользователь не найден или githubUsername равен null.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    Optional<User> findByGithubUsername(String githubUsername);

    /**
     * Возвращает список пользователей с указанной ролью.
     *
     * @param role роль пользователя (user, dev, mod, admin). Не должна быть null.
     *
     * @return список пользователей с указанной ролью. Никогда не возвращает null.
     *         Может быть пустым, если пользователей с такой ролью нет.
     *
     * @throws IllegalArgumentException                    если role равна null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    List<User> findByRole(User.Role role);

    /**
     * Проверяет существование пользователя по username.
     *
     * @param username имя пользователя. Не должно быть null или пустым.
     *
     * @return true если пользователь существует, иначе false.
     *
     * @throws IllegalArgumentException                    если username равен null
     *                                                     или пустой строке
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    boolean existsByUsername(String username);

    /**
     * Проверяет существование пользователя по email.
     * <p>
     * <strong>Важно:</strong> Для корректной работы этого метода необходимо
     * добавить
     * поле {@code email} в сущность {@link User} и соответствующую колонку в
     * таблицу БД.
     * </p>
     *
     * @param email email пользователя. Не должен быть null.
     *
     * @return true если пользователь с таким email существует, иначе false.
     *
     * @throws IllegalArgumentException                    если email равен null
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    boolean existsByEmail(String email);

    /**
     * Проверяет существование пользователя по GitHub username.
     *
     * @param githubUsername GitHub username. Может быть null.
     *
     * @return true если пользователь с таким GitHub username существует, иначе
     *         false.
     *         Всегда возвращает false, если githubUsername равен null.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    boolean existsByGithubUsername(String githubUsername);

    /**
     * Возвращает страницу пользователей
     * с поддержкой фильтрации и пагинации.
     *
     * <p>
     * Поддерживаются следующие фильтры:
     * </p>
     * <ul>
     *     <li>поиск по имени пользователя;</li>
     *     <li>поиск по email;</li>
     *     <li>фильтрация по роли пользователя.</li>
     * </ul>
     *
     * <p>
     * Поиск выполняется без учёта регистра.
     * </p>
     *
     * <p>
     * Если search равен null или пустой строке,
     * текстовый поиск не применяется.
     * </p>
     *
     * <p>
     * Если role равен null,
     * фильтрация по роли не применяется.
     * </p>
     *
     * @param search строка поискового запроса.
     *               Может быть null или пустой строкой.
     *
     * @param role роль пользователя.
     *             Может быть null.
     *
     * @param status статус блокировки: "blocked" — только заблокированные на момент now,
     *               "active" — только незаблокированные. null или пустая строка — все.
     *
     * @param now момент времени, на который проверяется блокировка.
     *
     * @param pageable параметры пагинации и сортировки.
     *                 Не должен быть null.
     *
     * @return страница пользователей,
     *         удовлетворяющих условиям фильтрации.
     *         Никогда не возвращает null.
     *
     * @throws IllegalArgumentException                    если pageable равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе
     *                                                     данных
     */
    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
            "(:role IS NULL OR :role = '' OR u.role = :role) AND " +
            // фильтр по блокировке выполняется в БД, чтобы пагинация считалась правильно
            "(:status IS NULL OR :status = '' " +
            " OR (:status = 'blocked' AND EXISTS (SELECT b FROM Ban b WHERE b.user = u " +
            "     AND b.startTime < :now AND b.endTime > :now)) " +
            " OR (:status = 'active' AND NOT EXISTS (SELECT b FROM Ban b WHERE b.user = u " +
            "     AND b.startTime < :now AND b.endTime > :now)))")
    Page<User> findWithFilters(
            @Param("search") String search,
            @Param("role") User.Role role,
            @Param("status") String status,
            @Param("now") Instant now,
            Pageable pageable);
}