package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.myng.entities.Ban;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.entities.Warning;
import ru.vlsu.myng.entities.Notification;
import ru.vlsu.myng.repositories.BanRepository;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.UserRepository;
import ru.vlsu.myng.repositories.WarningRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserListService {

    private final UserRepository userRepository;
    private final BanRepository banRepository;
    private final NotificationRepository notificationRepository;
    private final WarningRepository warningRepository;

    /**
     * Возвращает страницу пользователей с дополнительной информацией
     * о статусе блокировки (ban status).
     *
     * <p>
     * Поддерживается фильтрация по:
     * <ul>
     *     <li>поисковой строке (username/email);</li>
     *     <li>роли пользователя;</li>
     *     <li>статусу блокировки (blocked / active).</li>
     * </ul>
     * </p>
     *
     * <p>
     * Если параметр status не указан, возвращаются все пользователи,
     * а статус блокировки рассчитывается для каждого.
     * </p>
     *
     * <p>
     * Если status = "blocked", возвращаются только заблокированные пользователи.
     * Если status = "active", возвращаются только незаблокированные пользователи.
     * </p>
     *
     * @param pageable параметры пагинации и сортировки.
     *                 Не должен быть null.
     *
     * @param search строка поиска по username/email.
     *               Может быть null или пустой строкой.
     *
     * @param role роль пользователя для фильтрации.
     *             Может быть null.
     *
     * @param status статус блокировки ("blocked" | "active").
     *               Может быть null или пустым.
     *
     * @return объект UserListData, содержащий:
     *         страницу пользователей и карту их статусов блокировки.
     *
     * @throws IllegalArgumentException                    если pageable равен null
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional(readOnly = true)
    public UserListData getUserListWithBannedStatus(Pageable pageable, String search, User.Role role, String status) {
        Page<User> usersPage = userRepository.findWithFilters(
                search, role, status, Instant.now(), pageable);

        return new UserListData(usersPage, getBannedStatusForUsers(usersPage.getContent()));
    }

    /**
     * Проверяет активный бан пользователя на текущий момент времени.
     *
     * <p>
     * Бан считается активным, если текущее время находится
     * между startTime и endTime.
     * </p>
     *
     * @param users список пользователей для проверки.
     *              Не должен быть null.
     *
     * @return карта userId → статус блокировки (true = забанен).
     */
    private Map<Integer, Boolean> getBannedStatusForUsers(List<User> users) {
        Map<Integer, Boolean> bannedMap = new HashMap<>();
        Instant now = Instant.now();

        for (User user : users) {
            boolean isBanned = banRepository.existsByUser_IdAndStartTimeBeforeAndEndTimeAfter(
                    user.getId(), now, now);
            bannedMap.put(user.getId(), isBanned);
        }

        return bannedMap;
    }

    /**
     * Внутренний класс для передачи данных о пользователях и их статусах.
     */
    @lombok.Value
    public static class UserListData {
        Page<User> users;
        Map<Integer, Boolean> bannedMap;
    }

    /**
     * Блокирует пользователя на указанный срок.
     *
     * <p>
     * Если durationHours:
     * <ul>
     *     <li>null или <= 0 — бан считается бессрочным;</li>
     *     <li>> 0 — бан устанавливается на указанное количество часов.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Один пользователь не может иметь более одного активного бана.
     * </p>
     *
     * @param userId идентификатор пользователя.
     * @param reason причина блокировки.
     * @param durationHours длительность блокировки в часах.
     * @param moderator администратор/модератор, выдавший бан.
     *
     * @throws RuntimeException если пользователь не найден
     *                           или уже забанен
     */
    @Transactional
    public void banUser(Integer userId, String reason, Integer durationHours, User moderator) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        boolean alreadyBanned = banRepository.existsByUser_IdAndStartTimeBeforeAndEndTimeAfter(
                userId, Instant.now(), Instant.now());
        if (alreadyBanned) {
            throw new RuntimeException("Пользователь уже заблокирован");
        }

        Ban ban = new Ban();
        ban.setUser(user);
        ban.setModerator(moderator);
        ban.setReason(reason);
        ban.setStartTime(Instant.now());

        if (durationHours == null || durationHours <= 0) {
            // Бессрочная блокировка
            ban.setEndTime(Instant.parse("2038-01-19T03:14:07Z"));
        } else {
            ban.setEndTime(Instant.now().plus(durationHours, ChronoUnit.HOURS));

            Instant maxDate = Instant.parse("2038-01-19T03:14:07Z");
            if (ban.getEndTime().isAfter(maxDate)) {
                ban.setEndTime(maxDate);
            }
        }

        banRepository.save(ban);
    }

    /**
     * Снимает активный бан с пользователя.
     *
     * <p>
     * Если активного бана нет — выбрасывается исключение.
     * </p>
     *
     * <p>
     * Фактически бан не удаляется, а его endTime
     * устанавливается в текущее время.
     * </p>
     *
     * @param userId идентификатор пользователя.
     *
     * @throws RuntimeException если пользователь не найден
     *                           или не имеет активного бана
     */
    @Transactional
    public void unbanUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Instant now = Instant.now();
        boolean hasActiveBan = banRepository.existsByUser_IdAndStartTimeBeforeAndEndTimeAfter(
                userId, now, now);

        if (!hasActiveBan) {
            throw new RuntimeException("У пользователя нет активной блокировки");
        }

        List<Ban> userBans = banRepository.findByUser(user);

        userBans.stream()
                .filter(ban -> ban.getEndTime().isAfter(now))
                .findFirst()
                .ifPresent(ban -> {
                    ban.setEndTime(now);
                    banRepository.save(ban);
                });
    }

    /**
     * Изменяет роль пользователя.
     *
     * <p>
     * Запрещено:
     * <ul>
     *     <li>изменять собственную роль;</li>
     *     <li>назначать ту же самую роль повторно.</li>
     * </ul>
     * </p>
     *
     * <p>
     * После изменения роли пользователю отправляется уведомление.
     * </p>
     *
     * @param userId идентификатор пользователя.
     * @param newRole новая роль пользователя.
     * @param admin администратор, выполняющий изменение.
     *
     * @throws RuntimeException если пользователь не найден,
     *                           или изменение роли недопустимо
     */
    @Transactional
    public void changeUserRole(Integer userId, User.Role newRole, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (user.getId().equals(admin.getId())) {
            throw new RuntimeException("Нельзя изменить свою собственную роль");
        }

        if (user.getRole() == newRole) {
            throw new RuntimeException("У пользователя уже эта роль");
        }

        User.Role oldRole = user.getRole();

        user.setRole(newRole);
        userRepository.save(user);

        createRoleChangeNotification(user, oldRole, newRole, admin);
    }

    /**
     * Создаёт системное уведомление об изменении роли пользователя.
     */
    private void createRoleChangeNotification(User user, User.Role oldRole, User.Role newRole, User admin) {
        String notificationText = String.format(
                "Ваша роль изменена с '%s' на '%s' (изменено администратором @%s)",
                getRoleDisplayName(oldRole),
                getRoleDisplayName(newRole),
                admin.getUsername());

        Notification notification = new Notification();
        notification.setCreatedAt(Instant.now());
        notification.setType(Notification.Type.system);
        notification.setText(notificationText);

        notification.setUsers(new HashSet<>());
        notification.getUsers().add(user);

        notificationRepository.save(notification);
    }

    /**
     * Возвращает человеко-читаемое отображение роли пользователя.
     */
    private String getRoleDisplayName(User.Role role) {
        return switch (role) {
            case user -> "пользователь";
            case dev -> "разработчик";
            case mod -> "модератор";
            case admin -> "администратор";
        };
    }

    /**
     * Выдаёт предупреждение пользователю и создаёт запись в системе.
     *
     * @param userId идентификатор пользователя.
     * @param reason причина предупреждения.
     * @param moderator модератор, выдающий предупреждение.
     *
     * @throws RuntimeException если пользователь не найден
     *                           или причина пуста
     */
    @Transactional
    public void issueWarning(Integer userId, String reason, User moderator) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Причина предупреждения не может быть пустой");
        }

        Warning warning = new Warning();
        warning.setUser(user);
        warning.setModerator(moderator);
        warning.setReason(reason);

        warningRepository.save(warning);

        createWarningNotification(user, reason, moderator);
    }

    /**
     * Создаёт уведомление о предупреждении пользователя.
     */
    private void createWarningNotification(User user, String reason, User moderator) {
        String notificationText = String.format(
                "Вам вынесено предупреждение от модератора @%s: %s",
                moderator.getUsername(),
                reason);

        Notification notification = new Notification();
        notification.setCreatedAt(Instant.now());
        notification.setType(Notification.Type.warning);
        notification.setText(notificationText);

        notification.setUsers(new HashSet<>());
        notification.getUsers().add(user);

        notificationRepository.save(notification);
    }
}