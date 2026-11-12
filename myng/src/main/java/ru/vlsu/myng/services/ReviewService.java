package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import ru.vlsu.myng.dto.ReviewUpdate;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.Notification;
import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.ReviewRepository;
import ru.vlsu.myng.repositories.UserRepository;
import ru.vlsu.myng.utils.NotFoundException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ModerationVerdictRepository verdictRepository;

    /**
     * Количество жалоб, после которого отзыв скрывается и отправляется на модерацию.
     */
    public static final int REPORT_THRESHOLD = 10;

    /**
     * Скрыт ли отзыв (набрал порог жалоб и ждёт модерации или отклонён модератором).
     * Скрытые отзывы не показываются и не учитываются в рейтинге игры.
     */
    public static boolean isHidden(Review review) {
        return review.getReportCount() >= REPORT_THRESHOLD;
    }

    /**
     * Обновляет текст и рейтинг отзыва.
     *
     * <p>
     * Выполняется поиск отзыва по идентификатору,
     * после чего обновляются поля text и rating.
     * Сумма оценок игры корректируется на разницу между старой
     * и новой оценкой, количество отзывов не меняется.
     * </p>
     *
     * @param reviewId идентификатор отзыва.
     *                 Не должен быть null.
     *
     * @param dto DTO с новыми данными отзыва.
     *            Не должен быть null.
     *
     * @param currentUser пользователь, выполняющий изменение.
     *                    Изменять можно только свой отзыв.
     *
     * @throws NotFoundException                           если отзыв с указанным id не найден
     * @throws AccessDeniedException                       если отзыв принадлежит другому пользователю
     * @throws IllegalArgumentException                    если оценка или текст некорректны
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public void updateReview(Integer reviewId, ReviewUpdate dto, User currentUser) {
        Review review = getOwnReview(reviewId, currentUser);

        validateReviewContent(dto.getRating(), dto.getText());

        int oldRating = review.getRating();

        review.setText(dto.getText().trim());
        review.setRating(dto.getRating());

        if (!isHidden(review)) {
            Game game = review.getGame();
            game.setRatingSum(game.getRatingSum() - oldRating + dto.getRating());
            recalculateAverageRating(game);
        }

        reviewRepository.save(review);
    }

    /**
     * Проверяет оценку и текст отзыва (те же правила, что и при создании).
     *
     * @throws IllegalArgumentException если оценка вне диапазона 1–5
     *                                  или текст короче 10 символов
     */
    private void validateReviewContent(Byte rating, String text) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Оценка должна быть от 1 до 5");
        }

        if (text == null || text.trim().length() < 10) {
            throw new IllegalArgumentException("Текст отзыва должен содержать минимум 10 символов");
        }
    }

    /**
     * Учитывает оценку отзыва в рейтинге игры.
     */
    private void addToGameRating(Review review) {
        Game game = review.getGame();
        game.setRatingSum(game.getRatingSum() + review.getRating());
        game.setReviewCount(game.getReviewCount() + 1);
        recalculateAverageRating(game);
    }

    /**
     * Убирает оценку отзыва из рейтинга игры.
     */
    private void removeFromGameRating(Review review) {
        Game game = review.getGame();
        game.setRatingSum(game.getRatingSum() - review.getRating());
        game.setReviewCount(game.getReviewCount() - 1);
        recalculateAverageRating(game);
    }

    /**
     * Пересчитывает средний рейтинг игры по сумме оценок и количеству отзывов.
     */
    private void recalculateAverageRating(Game game) {
        game.setAverageRating(
                game.getReviewCount() == 0
                        ? 0.0
                        : (double) game.getRatingSum() / game.getReviewCount());
    }

    /**
     * Возвращает отзыв по идентификатору.
     *
     * <p>
     * Метод выполняется в режиме read-only транзакции.
     * </p>
     *
     * @param id идентификатор отзыва.
     *           Не должен быть null.
     *
     * @return найденный отзыв.
     *
     * @throws NotFoundException                          если отзыв не найден
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional(readOnly = true)
    public Review getReviewById(Integer id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отзыв не найден"));
    }

    /**
     * Возвращает отзыв, проверив, что его автор — текущий пользователь.
     *
     * @throws NotFoundException     если отзыв не найден
     * @throws AccessDeniedException если отзыв принадлежит другому пользователю
     */
    private Review getOwnReview(Integer reviewId, User currentUser) {
        Review review = getReviewById(reviewId);

        if (!review.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Можно изменять только свои отзывы");
        }

        return review;
    }

    /**
     * Возвращает список отзывов, оставленных указанным пользователем.
     *
     * <p>
     * Сначала выполняется поиск пользователя,
     * затем загружаются все его отзывы.
     * </p>
     *
     * @param userId идентификатор пользователя.
     *               Не должен быть null.
     *
     * @return список отзывов пользователя.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если отзывов нет.
     *
     * @throws IllegalArgumentException                    если пользователь с указанным id не найден
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    public List<Review> getByUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return reviewRepository.findByUser(user);
    }

    public List<Review> getByGame(Game game) {
        return reviewRepository.findByGame(game);
    }

    public Optional<Review> getUserGameReview(User user, Game game) { return reviewRepository.findByUserAndGame(user, game); }

    public boolean reviewExists(User user, Game game) {
        return reviewRepository.existsByUserAndGame(user, game);
    }

    /**
     * Сохраняет отзыв без изменения статистики игры.
     *
     * <p>
     * Для создания, изменения и удаления отзыва используйте
     * {@link #createReview}, {@link #updateReview} и {@link #delete} —
     * они поддерживают рейтинг игры в актуальном состоянии.
     * </p>
     *
     * @param review отзыв для сохранения. Не должен быть null.
     *
     * @return сохранённый отзыв.
     *
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public Review save(Review review) {
        return reviewRepository.save(review);
    }

    /**
     * Удаляет отзыв по идентификатору и обновляет статистику игры.
     *
     * <p>
     * При удалении отзыва пересчитываются:
     * <ul>
     *     <li>сумма рейтингов (ratingSum);</li>
     *     <li>количество отзывов (reviewCount);</li>
     *     <li>средний рейтинг (averageRating).</li>
     * </ul>
     * </p>
     *
     * <p>
     * Если после удаления отзывов не остаётся,
     * средний рейтинг устанавливается в 0.0.
     * </p>
     *
     * @param id идентификатор отзыва.
     *           Не должен быть null.
     *
     * @throws NotFoundException                           если отзыв не найден
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public void delete(Integer id) {
        Review review = getReviewById(id);

        // скрытый отзыв уже исключён из рейтинга
        if (!isHidden(review)) {
            removeFromGameRating(review);
        }

        reviewRepository.delete(review);
    }

    /**
     * Удаляет отзыв по запросу его автора.
     *
     * @param id          идентификатор отзыва.
     * @param currentUser пользователь, выполняющий удаление.
     *
     * @throws NotFoundException     если отзыв не найден
     * @throws AccessDeniedException если отзыв принадлежит другому пользователю
     */
    @Transactional
    public void deleteOwnReview(Integer id, User currentUser) {
        getOwnReview(id, currentUser);
        delete(id);
    }

    @Transactional(readOnly = true)
    public boolean hasUserReviewedGame(Game game, User user) { return reviewRepository.existsByGameAndUser(game, user); }

    /**
     * Создаёт новый отзыв и обновляет агрегированную статистику игры.
     *
     * <p>
     * При создании отзыва:
     * <ul>
     *     <li>заполняются базовые поля отзыва;</li>
     *     <li>обновляется сумма рейтингов игры;</li>
     *     <li>увеличивается количество отзывов;</li>
     *     <li>пересчитывается средний рейтинг.</li>
     * </ul>
     * </p>
     *
     * @param game игра, для которой создаётся отзыв.
     *             Не должна быть null.
     *             Должна быть персистентной сущностью (id != null).
     *
     * @param user пользователь — автор отзыва.
     *             Не должен быть null.
     *             Должен быть персистентной сущностью (id != null).
     *
     * @param rating рейтинг отзыва.
     *               Не должен быть null.
     *               Ожидается значение в допустимом диапазоне рейтингов.
     *
     * @param text текст отзыва.
     *             Не должен быть null.
     *             Будет обрезан через trim().
     *
     * @return сохранённый отзыв.
     *
     * @throws IllegalArgumentException                    если game, user, rating или text некорректны
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public Review createReview(Game game, User user, Byte rating, String text) {
        Review review = new Review();
        review.setGame(game);
        review.setUser(user);
        review.setRating(rating);
        review.setText(text.trim());
        review.setCreatedAt(Instant.now());
        review.setReportCount(0);

        addToGameRating(review);

        return reviewRepository.save(review);
    }

    /**
     * Увеличивает количество жалоб на отзыв.
     *
     * <p>
     * Если количество жалоб достигает порога ({@link #REPORT_THRESHOLD}):
     * <ul>
     *     <li>если отзыв ещё не проверялся модератором — он скрывается,
     *         исключается из рейтинга игры, создаётся вердикт на модерацию
     *         и автору отправляется предупреждение;</li>
     *     <li>если модератор уже оставил этот отзыв — счётчик сбрасывается.</li>
     * </ul>
     * </p>
     *
     * <p>
     * Жалобы на уже скрытый отзыв (ждёт модерации или отклонён) не учитываются.
     * </p>
     *
     * @param id идентификатор отзыва.
     *           Не должен быть null.
     *
     * @throws EntityNotFoundException                    если отзыв с указанным id не найден
     * @throws org.springframework.dao.DataAccessException
     *                                                    при ошибке доступа к базе данных
     */
    @Transactional()
    public void incrementReportCount(Integer id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Отзыв не найден с id: " + id));

        if (isHidden(review)) {
            return;
        }

        review.setReportCount(review.getReportCount() + 1);
        reviewRepository.save(review);

        if (review.getReportCount() >= REPORT_THRESHOLD) {

            boolean verdictExists = verdictRepository.existsByReview(review);

            if (!verdictExists) {
                removeFromGameRating(review);

                ModerationVerdict verdict = new ModerationVerdict();
                verdict.setReview(review);
                verdict.setApproved(null);

                verdictRepository.save(verdict);

                User reviewAuthor = review.getUser();
                String gameName = review.getGame().getName();

                String notificationText = String.format(
                        "Ваш отзыв на игру \"%s\" скрыт из-за большого количества жалоб (10+). " +
                                "Пожалуйста, ознакомьтесь с правилами платформы.",
                        gameName);

                createWarningNotification(reviewAuthor, notificationText);

            } else {
                // модератор уже проверил отзыв и оставил его
                review.setReportCount(0);
            }
        }
    }

    /**
     * Возвращает отзыв на сайт после того, как модератор решил его оставить:
     * сбрасывает жалобы и снова учитывает оценку в рейтинге игры.
     *
     * @param review отзыв, одобренный модератором. Не должен быть null.
     */
    @Transactional
    public void restoreAfterModeration(Review review) {
        if (isHidden(review)) {
            addToGameRating(review);
        }

        review.setReportCount(0);
        reviewRepository.save(review);
    }

    /**
     * Создаёт предупреждающее уведомление для пользователя.
     *
     * <p>
     * Уведомление относится к типу warning и отправляется
     * конкретному пользователю.
     * </p>
     *
     * @param user пользователь, которому отправляется уведомление.
     *             Не должен быть null.
     *
     * @param text текст уведомления.
     *             Не должен быть null.
     *
     * @throws IllegalArgumentException                    если user или text некорректны
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    private void createWarningNotification(User user, String text) {
        Notification notification = new Notification();
        notification.setCreatedAt(Instant.now());
        notification.setType(Notification.Type.warning);
        notification.setText(text);

        notification.setUsers(new HashSet<>());
        notification.getUsers().add(user);

        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Review> findByGameOrderByCreatedAtDesc(Game game, PageRequest pageable) {
        return reviewRepository.findRecentReviews(
                game.getId(),
                REPORT_THRESHOLD - 1,
                pageable);
    }
}