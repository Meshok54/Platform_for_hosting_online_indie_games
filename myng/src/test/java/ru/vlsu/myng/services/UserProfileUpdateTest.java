package ru.vlsu.myng.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Проверки при редактировании профиля: имя, описание, аватар.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileUpdateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1);
        user.setUsername("player");
        user.setBio("старое описание");
    }

    @Test
    void updateProfile_ShouldSaveTrimmedValues() {
        when(userRepository.existsByUsername("newname")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user, "  newname  ", "  о себе  ", null);

        assertEquals("newname", user.getUsername());
        assertEquals("о себе", user.getBio());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_ShouldAllowKeepingOwnUsername() {
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user, "player", "новое описание", null);

        verify(userRepository, never()).existsByUsername(any());
        assertEquals("новое описание", user.getBio());
    }

    @Test
    void updateProfile_ShouldRejectTakenUsername() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user, "taken", "", null));

        assertEquals("Имя пользователя уже занято", ex.getMessage());
        assertEquals("player", user.getUsername());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldRejectBlankOrTooLongUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user, "   ", "", null));

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user, "a".repeat(UserService.USERNAME_MAX_LENGTH + 1), "", null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldRejectTooLongBio() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user, "player", "x".repeat(UserService.BIO_MAX_LENGTH + 1), null));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldRejectNonImageAvatar() {
        MockMultipartFile script = new MockMultipartFile(
                "avatar", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user, "player", "", script));

        assertEquals("Аватар должен быть изображением", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldAcceptImageAvatar() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
        MockMultipartFile avatar = new MockMultipartFile("avatar", "me.png", "image/png", png);
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(user, "player", "", avatar);

        assertArrayEquals(png, user.getProfilePic());
    }
}
