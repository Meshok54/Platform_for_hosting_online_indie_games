package ru.vlsu.myng.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vlsu.myng.entities.DevApplication;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.DevApplicationRepository;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevApplicationServiceTest {

    @Mock
    private DevApplicationRepository devApplicationRepository;

    @Mock
    private ModerationVerdictRepository verdictRepository;

    @InjectMocks
    private DevApplicationService devApplicationService;

    private User player() {
        User user = new User();
        user.setId(1);
        user.setRole(User.Role.user);
        return user;
    }

    @Test
    void submit_ShouldCreateApplicationAndPendingVerdict() {
        User user = player();
        when(devApplicationRepository.findByUser(user)).thenReturn(List.of());

        DevApplication app = devApplicationService.submit(user, "octocat", "Хочу публиковать игры");

        assertEquals(user, app.getUser());
        assertEquals("octocat", app.getGithubUsername());
        assertNotNull(app.getCreatedAt());
        verify(devApplicationRepository).save(app);

        ArgumentCaptor<ModerationVerdict> verdict = ArgumentCaptor.forClass(ModerationVerdict.class);
        verify(verdictRepository).save(verdict.capture());
        assertEquals(app, verdict.getValue().getDevApplication());
        assertNull(verdict.getValue().getApproved(), "Заявка должна ждать решения модератора");
    }

    @Test
    void submit_ShouldReject_WhenUserIsAlreadyDeveloper() {
        User dev = player();
        dev.setRole(User.Role.dev);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> devApplicationService.submit(dev, "octocat", "текст"));

        assertEquals("У вас уже есть права разработчика", ex.getMessage());
        verify(devApplicationRepository, never()).save(any());
    }

    @Test
    void submit_ShouldReject_WhenApplicationIsAlreadyPending() {
        User user = player();
        DevApplication previous = new DevApplication();
        ModerationVerdict pending = new ModerationVerdict();

        when(devApplicationRepository.findByUser(user)).thenReturn(List.of(previous));
        when(verdictRepository.findByDevApplication(previous)).thenReturn(Optional.of(pending));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> devApplicationService.submit(user, "octocat", "текст"));

        assertEquals("Ваша заявка уже на рассмотрении", ex.getMessage());
        verify(devApplicationRepository, never()).save(any());
    }

    @Test
    void submit_ShouldAllowNewApplication_AfterRejection() {
        User user = player();
        DevApplication previous = new DevApplication();
        ModerationVerdict rejected = new ModerationVerdict();
        rejected.setApproved(false);

        when(devApplicationRepository.findByUser(user)).thenReturn(List.of(previous));
        when(verdictRepository.findByDevApplication(previous)).thenReturn(Optional.of(rejected));

        devApplicationService.submit(user, "octocat", "Вторая попытка");

        verify(devApplicationRepository).save(any(DevApplication.class));
    }
}
