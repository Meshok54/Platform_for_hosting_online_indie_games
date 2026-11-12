package ru.vlsu.myng.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import ru.vlsu.myng.entities.User;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Фильтр по блокировке в списке пользователей выполняется в БД,
 * поэтому количество страниц и элементов считается правильно.
 *
 * <p>
 * Тест только читает данные из локальной БД и проверяет согласованность результата,
 * поэтому не зависит от того, какие именно пользователи в ней есть.
 * </p>
 */
@SpringBootTest
class UserFilterQueryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BanRepository banRepository;

    @Test
    void blockedAndActiveFilters_SplitAllUsersWithCorrectTotals() {
        Instant now = Instant.now();
        PageRequest firstPage = PageRequest.of(0, 2);

        long all = userRepository.findWithFilters(null, null, null, now, firstPage).getTotalElements();
        Page<User> blocked = userRepository.findWithFilters(null, null, "blocked", now, firstPage);
        Page<User> active = userRepository.findWithFilters(null, null, "active", now, firstPage);

        assertEquals(all, blocked.getTotalElements() + active.getTotalElements(),
                "Заблокированные и активные вместе должны давать всех пользователей");

        blocked.forEach(user -> assertTrue(isBanned(user, now), user.getUsername() + " должен быть заблокирован"));
        active.forEach(user -> assertFalse(isBanned(user, now), user.getUsername() + " не должен быть заблокирован"));
    }

    private boolean isBanned(User user, Instant now) {
        return banRepository.existsByUser_IdAndStartTimeBeforeAndEndTimeAfter(user.getId(), now, now);
    }
}
