package ru.vlsu.myng.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.myng.entities.Ban;
import ru.vlsu.myng.entities.Notification;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.entities.Warning;
import ru.vlsu.myng.repositories.BanRepository;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.UserRepository;
import ru.vlsu.myng.repositories.WarningRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserListServiceTest extends BaseIntegrationTest {

    @Autowired
    private UserListService userListService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BanRepository banRepository;

    @Autowired
    private WarningRepository warningRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User targetUser;
    private User moderator;

    @BeforeEach
    void setup() {
        targetUser = new User();
        targetUser.setUsername("bad_player");
        targetUser.setEmail("bad@mail.com");
        targetUser.setPasswordHash("hash");
        targetUser.setRole(User.Role.user);
        userRepository.save(targetUser);

        moderator = new User();
        moderator.setUsername("admin_mod");
        moderator.setEmail("mod@mail.com");
        moderator.setPasswordHash("hash");
        moderator.setRole(User.Role.admin);
        userRepository.save(moderator);
    }

    @Test
    void banUser_shouldCreateBanSuccessfully() {
        String reason = "Нарушение правил чата";
        Integer durationHours = 24;

        userListService.banUser(targetUser.getId(), reason, durationHours, moderator);

        List<Ban> bans = banRepository.findAll();
        assertThat(bans).hasSize(1);

        Ban savedBan = bans.get(0);
        assertThat(savedBan.getReason()).isEqualTo(reason);
        assertThat(savedBan.getUser().getId()).isEqualTo(targetUser.getId());
        assertThat(savedBan.getModerator().getId()).isEqualTo(moderator.getId());

        assertThat(savedBan.getEndTime()).isAfter(Instant.now());
    }

    @Test
    void banUser_shouldSetMaxDate_whenDurationIsNull() {
        String reason = "Читерство";

        userListService.banUser(targetUser.getId(), reason, null, moderator);

        List<Ban> bans = banRepository.findAll();
        assertThat(bans).hasSize(1);

        Instant expectedMaxDate = Instant.parse("2038-01-19T03:14:07Z");
        assertThat(bans.get(0).getEndTime()).isEqualTo(expectedMaxDate);
    }

    @Test
    void banUser_shouldThrowException_whenUserAlreadyBanned() {
        Ban existingBan = new Ban();
        existingBan.setUser(targetUser);
        existingBan.setModerator(moderator);
        existingBan.setReason("Первый бан");
        existingBan.setStartTime(Instant.now().minusSeconds(3600));
        existingBan.setEndTime(Instant.now().plusSeconds(3600));
        banRepository.save(existingBan);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userListService.banUser(targetUser.getId(), "Второй бан", 24, moderator);
        });

        assertThat(exception.getMessage()).isEqualTo("Пользователь уже заблокирован");

        assertThat(banRepository.findAll()).hasSize(1);
    }

    // ==========================================
    // ТЕСТЫ ДЛЯ МЕТОДА unbanUser
    // ==========================================

    @Test
    void unbanUser_shouldEndBanSuccessfully() {
        Ban activeBan = new Ban();
        activeBan.setUser(targetUser);
        activeBan.setModerator(moderator);
        activeBan.setReason("Тестовый бан");
        activeBan.setStartTime(Instant.now().minusSeconds(3600));
        activeBan.setEndTime(Instant.now().plusSeconds(3600));
        banRepository.save(activeBan);

        userListService.unbanUser(targetUser.getId());

        List<Ban> bans = banRepository.findAll();
        assertThat(bans).hasSize(1);
        assertThat(bans.get(0).getEndTime()).isBeforeOrEqualTo(Instant.now());

        boolean hasActiveBan = banRepository.existsByUser_IdAndStartTimeBeforeAndEndTimeAfter(
                targetUser.getId(), Instant.now(), Instant.now());
        assertThat(hasActiveBan).isFalse();
    }

    @Test
    void unbanUser_shouldThrowException_whenNoActiveBan() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userListService.unbanUser(targetUser.getId());
        });

        assertThat(exception.getMessage()).isEqualTo("У пользователя нет активной блокировки");
    }

    // ==========================================
    // ТЕСТЫ ДЛЯ МЕТОДА changeUserRole
    // ==========================================

    @Test
    void changeUserRole_shouldChangeRoleAndCreateNotification() {
        userListService.changeUserRole(targetUser.getId(), User.Role.mod, moderator);

        User updatedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(User.Role.mod);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);

        Notification notification = notifications.get(0);
        assertThat(notification.getType()).isEqualTo(Notification.Type.system);
        assertThat(notification.getText()).contains("Ваша роль изменена");
        assertThat(notification.getUsers()).contains(updatedUser);
    }

    @Test
    void changeUserRole_shouldThrowException_whenChangingOwnRole() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userListService.changeUserRole(moderator.getId(), User.Role.user, moderator);
        });

        assertThat(exception.getMessage()).isEqualTo("Нельзя изменить свою собственную роль");
    }

    @Test
    void changeUserRole_shouldThrowException_whenRoleIsSame() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userListService.changeUserRole(targetUser.getId(), User.Role.user, moderator);
        });

        assertThat(exception.getMessage()).isEqualTo("У пользователя уже эта роль");
    }

    // ==========================================
    // ТЕСТЫ ДЛЯ МЕТОДА issueWarning
    // ==========================================

    @Test
    void issueWarning_shouldCreateWarningAndNotification() {
        String reason = "Спам в комментариях";

        userListService.issueWarning(targetUser.getId(), reason, moderator);

        List<Warning> warnings = warningRepository.findAll();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getReason()).isEqualTo(reason);
        assertThat(warnings.get(0).getUser().getId()).isEqualTo(targetUser.getId());
        assertThat(warnings.get(0).getModerator().getId()).isEqualTo(moderator.getId());

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(Notification.Type.warning);
        assertThat(notifications.get(0).getText()).contains("Вам вынесено предупреждение");
        assertThat(notifications.get(0).getText()).contains(reason);
    }

    @Test
    void banUser_shouldRejectBanningYourself() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userListService.banUser(moderator.getId(), "Причина", 24, moderator);
        });

        assertThat(exception.getMessage()).isEqualTo("Нельзя заблокировать самого себя");
        assertThat(banRepository.findByUser(moderator)).isEmpty();
    }

    @Test
    void banUser_shouldRejectBanningAnotherAdmin() {
        User otherAdmin = new User();
        otherAdmin.setUsername("second_admin");
        otherAdmin.setEmail("admin2@mail.com");
        otherAdmin.setPasswordHash("hash");
        otherAdmin.setRole(User.Role.admin);
        userRepository.save(otherAdmin);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userListService.banUser(otherAdmin.getId(), "Причина", 24, moderator);
        });

        assertThat(exception.getMessage()).isEqualTo("Нельзя заблокировать администратора");
        assertThat(banRepository.findByUser(otherAdmin)).isEmpty();
    }

    @Test
    void banUser_shouldRejectEmptyReason() {
        assertThrows(IllegalArgumentException.class, () -> {
            userListService.banUser(targetUser.getId(), "   ", 24, moderator);
        });

        assertThat(banRepository.findByUser(targetUser)).isEmpty();
    }

    @Test
    void issueWarning_shouldThrowException_whenReasonIsEmpty() {
        assertThrows(RuntimeException.class, () -> {
            userListService.issueWarning(targetUser.getId(), "   ", moderator);
        });
    }

    // ==========================================
    // ТЕСТЫ ДЛЯ МЕТОДА getUserListWithBannedStatus
    // ==========================================

    @Test
    void getUserListWithBannedStatus_shouldReturnCorrectMapAndFilterByStatus() {
        User bannedUser = new User();
        bannedUser.setUsername("hacker");
        bannedUser.setEmail("hacker@mail.com");
        bannedUser.setPasswordHash("hash");
        bannedUser.setRole(User.Role.user);
        userRepository.save(bannedUser);

        Ban ban = new Ban();
        ban.setUser(bannedUser);
        ban.setModerator(moderator);
        ban.setReason("Использование читов");
        ban.setStartTime(Instant.now().minusSeconds(100));
        ban.setEndTime(Instant.now().plus(1, ChronoUnit.DAYS));
        banRepository.save(ban);

        UserListService.UserListData dataAll = userListService.getUserListWithBannedStatus(
                PageRequest.of(0, 10), null, null, null);

        assertThat(dataAll.getUsers().getContent()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(dataAll.getBannedMap().get(bannedUser.getId())).isTrue();
        assertThat(dataAll.getBannedMap().get(targetUser.getId())).isFalse();

        UserListService.UserListData dataBlocked = userListService.getUserListWithBannedStatus(
                PageRequest.of(0, 10), null, null, "blocked");

        List<Integer> blockedIds = dataBlocked.getUsers().getContent().stream().map(User::getId).toList();
        assertThat(blockedIds).contains(bannedUser.getId());
        assertThat(blockedIds).doesNotContain(targetUser.getId());
    }
}