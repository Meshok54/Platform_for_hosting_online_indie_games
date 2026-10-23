package ru.vlsu.myng.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.vlsu.myng.dto.ReviewUpdate;
import ru.vlsu.myng.utils.NotFoundException;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.Notification;
import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.ReviewRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ModerationVerdictRepository verdictRepository;

    @InjectMocks
    private ReviewService reviewService;

    // ==========================================
    // 1. ТЕСТЫ ДЛЯ МЕТОДА save()
    // ==========================================

    @Test
    void save_ShouldRecalculateGameRating_WhenReviewIsPresent() {

        Game game = new Game();
        game.setId(1);
        game.setRatingSum(10);
        game.setReviewCount(2);
        game.setAverageRating(5.0);

        Review review = new Review();
        review.setId(100);
        review.setRating((byte) 5);

        when(reviewRepository.findGameById(100)).thenReturn(Optional.of(game));
        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));
        when(reviewRepository.save(review)).thenReturn(review);

        Review savedReview = reviewService.save(review);

        assertNotNull(savedReview);
        assertEquals(15, game.getRatingSum(), "Сумма рейтингов должна увеличиться на 5");
        assertEquals(3, game.getReviewCount(), "Количество отзывов должно увеличиться на 1");
        assertEquals(5.0, game.getAverageRating(), "Средний рейтинг должен пересчитаться (15 / 3 = 5.0)");
        verify(reviewRepository, times(1)).save(review);
    }

    // ==========================================
    // 2. ТЕСТЫ ДЛЯ МЕТОДА delete()
    // ==========================================

    @Test
    void delete_ShouldRecalculateRatingAndDecrementCount() {
        Game game = new Game();
        game.setId(1);
        game.setRatingSum(8);
        game.setReviewCount(2);
        game.setAverageRating(4.0);

        Review review = new Review();
        review.setId(100);
        review.setRating((byte) 4);

        when(reviewRepository.findGameById(100)).thenReturn(Optional.of(game));
        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));

        reviewService.delete(100);

        assertEquals(4, game.getRatingSum(), "Сумма рейтингов должна уменьшиться на 4");
        assertEquals(1, game.getReviewCount(), "Количество отзывов должно уменьшиться на 1");
        assertEquals(4.0, game.getAverageRating(), "Средний рейтинг должен стать 4.0 (4 / 1)");
        verify(reviewRepository, times(1)).deleteById(100);
    }

    @Test
    void delete_ShouldSetAverageRatingToZero_WhenLastReviewDeleted() {
        Game game = new Game();
        game.setId(1);
        game.setRatingSum(5);
        game.setReviewCount(1);

        Review review = new Review();
        review.setId(100);
        review.setRating((byte) 5);

        when(reviewRepository.findGameById(100)).thenReturn(Optional.of(game));
        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));

        reviewService.delete(100);

        assertEquals(0, game.getReviewCount());
        assertEquals(0.0, game.getAverageRating(),
                "Средний рейтинг должен сброситься в 0.0 во избежание деления на ноль");
        verify(reviewRepository, times(1)).deleteById(100);
    }

    // ==========================================
    // 3. ТЕСТЫ ДЛЯ МЕТОДА createReview()
    // ==========================================

    @Test
    void createReview_ShouldTrimTextAndInitializeFieldsAndModifyGame() {
        Game game = new Game();
        game.setRatingSum(0);
        game.setReviewCount(0);

        User user = new User();
        String rawText = "   Отличный каталог игр!   ";

        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review created = reviewService.createReview(game, user, (byte) 5, rawText);

        assertNotNull(created);
        assertEquals("Отличный каталог игр!", created.getText(),
                "Пробелы по краям текста должны быть удалены через trim()");
        assertEquals(0, created.getReportCount());
        assertNotNull(created.getCreatedAt());
        assertEquals(game, created.getGame());
        assertEquals(user, created.getUser());

        assertEquals(5, game.getRatingSum());
        assertEquals(1, game.getReviewCount());
        assertEquals(5.0, game.getAverageRating());
    }

    // ==========================================
    // 4. ТЕСТЫ ДЛЯ МЕТОДА incrementReportCount()
    // ==========================================

    @Test
    void incrementReportCount_ShouldJustIncrement_WhenReportsLessThanTen() {
        Review review = new Review();
        review.setId(50);
        review.setReportCount(3);

        when(reviewRepository.findById(50)).thenReturn(Optional.of(review));

        reviewService.incrementReportCount(50);

        assertEquals(4, review.getReportCount(), "Количество жалоб должно увеличиться на 1");

        verifyNoInteractions(verdictRepository, notificationRepository);
        verify(reviewRepository, times(1)).save(review);
    }

    @Test
    void incrementReportCount_ShouldCreateVerdictAndWarning_WhenReportsReachTenAndNoVerdictExists() {
        User author = new User();
        Game game = new Game();
        game.setName("Тестовая Игра");

        Review review = new Review();
        review.setId(50);
        review.setReportCount(9);
        review.setUser(author);
        review.setGame(game);

        when(reviewRepository.findById(50)).thenReturn(Optional.of(review));
        when(verdictRepository.existsByReview(review)).thenReturn(false);

        reviewService.incrementReportCount(50);

        assertEquals(10, review.getReportCount());

        ArgumentCaptor<ModerationVerdict> verdictCaptor = ArgumentCaptor.forClass(ModerationVerdict.class);
        verify(verdictRepository, times(1)).save(verdictCaptor.capture());
        ModerationVerdict savedVerdict = verdictCaptor.getValue();
        assertNull(savedVerdict.getApproved(), "Статус одобрения вердикта должен быть null (на рассмотрении)");
        assertEquals(review, savedVerdict.getReview());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertEquals(Notification.Type.warning, savedNotification.getType());
        assertTrue(savedNotification.getText().contains("Тестовая Игра"), "Текст должен содержать имя игры");
        assertTrue(savedNotification.getUsers().contains(author), "Уведомление должно быть привязано к автору отзыва");
    }

    @Test
    void incrementReportCount_ShouldResetReportsToZero_WhenReportsReachTenButVerdictAlreadyExists() {
        Review review = new Review();
        review.setId(50);
        review.setReportCount(9);

        when(reviewRepository.findById(50)).thenReturn(Optional.of(review));
        when(verdictRepository.existsByReview(review)).thenReturn(true);

        reviewService.incrementReportCount(50);

        assertEquals(0, review.getReportCount(), "Счетчик жалоб должен сброситься в 0 согласно блоку else");
        verify(reviewRepository, times(1)).save(review);
    }

    @Test
    void incrementReportCount_ShouldThrowException_WhenReviewNotFound() {
        when(reviewRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            reviewService.incrementReportCount(999);
        });
    }

    // ==========================================
    // Проверка автора отзыва
    // ==========================================

    @Test
    void deleteOwnReview_ShouldThrow_WhenReviewBelongsToAnotherUser() {
        User author = new User();
        author.setId(1);
        User otherUser = new User();
        otherUser.setId(2);

        Review review = new Review();
        review.setId(100);
        review.setUser(author);

        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class,
                () -> reviewService.deleteOwnReview(100, otherUser));

        verify(reviewRepository, never()).deleteById(any());
    }

    @Test
    void deleteOwnReview_ShouldDelete_WhenUserIsAuthor() {
        User author = new User();
        author.setId(1);

        Review review = new Review();
        review.setId(100);
        review.setUser(author);
        review.setRating((byte) 4);

        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));
        when(reviewRepository.findGameById(100)).thenReturn(Optional.empty());

        reviewService.deleteOwnReview(100, author);

        verify(reviewRepository).deleteById(100);
    }

    @Test
    void updateReview_ShouldThrow_WhenReviewBelongsToAnotherUser() {
        User author = new User();
        author.setId(1);
        User otherUser = new User();
        otherUser.setId(2);

        Review review = new Review();
        review.setId(100);
        review.setUser(author);
        review.setText("Исходный текст");

        ReviewUpdate dto = new ReviewUpdate();
        dto.setText("Чужая правка");
        dto.setRating((byte) 1);

        when(reviewRepository.findById(100)).thenReturn(Optional.of(review));

        assertThrows(AccessDeniedException.class,
                () -> reviewService.updateReview(100, dto, otherUser));

        assertEquals("Исходный текст", review.getText());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void getReviewById_ShouldThrowNotFound_WhenReviewMissing() {
        when(reviewRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reviewService.getReviewById(999));
    }
}
