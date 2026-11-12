package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import ru.vlsu.myng.entities.*;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.UserRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

/**
 * Сервис для управления решениями модерации.<br>
 * <br>
 * Обеспечивает бизнес-логику для вынесения решений модераторами:<br>
 * - сохранение нового вердикта модерации;<br>
 * - поиск вердикта по заявке разработчика;<br>
 * - одобрение (approve) версии игры, заявки разработчика или отзыва;<br>
 * - отклонение (reject) с указанием причины;<br>
 * - автоматическое создание уведомлений при вынесении решения;<br>
 * - загрузка файлов игры при одобрении версии.<br>
 * <br>
 * Используется в следующих сценариях:<br>
 * - модератор одобряет новую версию игры (файлы загружаются с GitHub);<br>
 * - модератор одобряет заявку пользователя на роль разработчика
 * (пользователь получает роль DEV);<br>
 * - модератор одобряет отзыв, на который поступили жалобы
 * (счётчик жалоб сбрасывается);<br>
 * - модератор отклоняет версию, заявку или отзыв с указанием причины;<br>
 * - система уведомляет пользователей о результатах модерации.<br>
 * <br>
 * Каждое решение модерации может быть вынесено только один раз —
 * повторное одобрение или отклонение уже решённого вердикта
 * вызывает {@link IllegalStateException}.
 * После вынесения решения автоматически создаётся уведомление
 * типа {@link Notification.Type#moderation} для затронутого пользователя.
 */
@Service
@RequiredArgsConstructor
public class ModerationVerdictService {
    private final ModerationVerdictRepository moderationVerdictRepository;
    private final GithubService githubService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ReviewService reviewService;

    /**
     * Сохраняет или обновляет решение модерации в базе данных.
     *
     * @param verdict решение модерации для сохранения. Не должно быть null.
     *
     * @return сохранённое решение с актуальными данными.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public ModerationVerdict save(ru.vlsu.myng.entities.ModerationVerdict verdict) {
        return moderationVerdictRepository.save(verdict);
    }

    /**
     * Находит решение модерации по заявке разработчика.
     *
     * <p>
     * Каждая заявка разработчика может иметь не более одного вердикта.
     * </p>
     *
     * @param app заявка разработчика. Не должна быть null.
     *
     * @return Optional с решением модерации.
     *         Optional.empty() если решение по заявке ещё не вынесено.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Optional<ModerationVerdict> findByDevApplication(DevApplication app) {
        return moderationVerdictRepository.findByDevApplication(app);
    }

    /**
     * Одобряет решение модерации.
     *
     * <p>
     * Действия зависят от типа связанной сущности:
     * </p>
     * <ul>
     * <li><b>GAME_VERSION</b> — загружает файлы игры с GitHub
     * через {@link GithubService#downloadGameVersion},
     * уведомляет разработчика о публикации версии;</li>
     * <li><b>DEV_APPLICATION</b> — изменяет роль пользователя
     * на {@link User.Role#dev}, уведомляет о получении статуса
     * разработчика;</li>
     * <li><b>REVIEW</b> — сбрасывает счётчик жалоб на отзыв
     * в 0, уведомляет автора об успешной модерации.</li>
     * </ul>
     *
     * <p>
     * После вынесения решения в базе данных сохраняется:
     * </p>
     * <ul>
     * <li>обновлённый вердикт с флагом approved = true
     * и ссылкой на модератора;</li>
     * <li>уведомление типа {@link Notification.Type#moderation}
     * для затронутого пользователя.</li>
     * </ul>
     *
     * @param moderationVerdictId идентификатор решения модерации.
     *                            Не должен быть null.
     * @param user                текущий пользователь Spring Security
     *                            (модератор). Не должен быть null.
     *
     * @throws RuntimeException                            если решение с указанным
     *                                                     id не найдено
     * @throws IllegalStateException                       если решение уже было
     *                                                     вынесено ранее
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void approve(Integer moderationVerdictId, org.springframework.security.core.userdetails.User user) {
        ModerationVerdict verdict = moderationVerdictRepository.findById(moderationVerdictId)
                .orElseThrow(() -> new RuntimeException("Moderation verdict not found"));

        if (verdict.getApproved() != null) {
            throw new IllegalStateException("Verdict already decided");
        }

        User moderator = userService.findByEmail(user.getUsername());
        User recipient = null;
        String notificationText = null;
        String gameName = null;

        var gameVersion = verdict.getGameVersion();
        if (gameVersion != null) {
            githubService.downloadGameVersion(gameVersion);
            recipient = gameVersion.getGame().getDeveloper();
            gameName = gameVersion.getGame().getName();
            notificationText = String.format(
                    "Ваша версия игры \"%s\" одобрена модератором @%s и опубликована",
                    gameName,
                    moderator.getUsername());
        }

        var devApp = verdict.getDevApplication();
        if (devApp != null) {
            recipient = devApp.getUser();
            recipient.setRole(User.Role.dev);
            userRepository.save(recipient);
            notificationText = String.format(
                    "Ваша заявка на статус разработчика одобрена модератором @%s! Теперь вы можете публиковать игры",
                    moderator.getUsername());
        }

        var review = verdict.getReview();
        if (review != null) {
            recipient = review.getUser();
            gameName = review.getGame().getName();
            notificationText = String.format(
                    "Ваш отзыв на игру \"%s\" прошел модерацию и не удален",
                    gameName);
            reviewService.restoreAfterModeration(review);
        }

        verdict.setApproved(true);
        verdict.setModerator(moderator);
        moderationVerdictRepository.save(verdict);

        if (recipient != null && notificationText != null) {
            createNotification(recipient, notificationText, Notification.Type.moderation);
        }
    }

    /**
     * Отклоняет решение модерации с указанием причины.
     *
     * <p>
     * Действия зависят от типа связанной сущности:
     * </p>
     * <ul>
     * <li><b>GAME_VERSION</b> — уведомляет разработчика об отклонении
     * версии с указанием причины;</li>
     * <li><b>DEV_APPLICATION</b> — уведомляет пользователя об отклонении
     * заявки на статус разработчика с указанием причины;</li>
     * <li><b>REVIEW</b> — уведомляет автора о скрытии отзыва
     * с указанием причины (отзыв скрывается навсегда).</li>
     * </ul>
     *
     * <p>
     * После вынесения решения в базе данных сохраняется:
     * </p>
     * <ul>
     * <li>обновлённый вердикт с флагом approved = false,
     * причиной отклонения и ссылкой на модератора;</li>
     * <li>уведомление типа {@link Notification.Type#moderation}
     * для затронутого пользователя.</li>
     * </ul>
     *
     * @param moderationVerdictId идентификатор решения модерации.
     *                            Не должен быть null.
     * @param reason              причина отклонения. Не должна быть null или
     *                            пустой.
     * @param user                текущий пользователь Spring Security
     *                            (модератор). Не должен быть null.
     *
     * @throws RuntimeException                            если решение с указанным
     *                                                     id не найдено
     * @throws IllegalStateException                       если решение уже было
     *                                                     вынесено ранее
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void reject(Integer moderationVerdictId, String reason,
            org.springframework.security.core.userdetails.User user) {
        ModerationVerdict verdict = moderationVerdictRepository.findById(moderationVerdictId)
                .orElseThrow(() -> new RuntimeException("Moderation verdict not found"));

        if (verdict.getApproved() != null) {
            throw new IllegalStateException("Verdict already decided");
        }

        User moderator = userService.findByEmail(user.getUsername());
        User recipient = null;
        String notificationText = null;
        String gameName = null;

        var gameVersion = verdict.getGameVersion();
        if (gameVersion != null) {
            recipient = gameVersion.getGame().getDeveloper();
            gameName = gameVersion.getGame().getName();
            notificationText = String.format(
                    "Ваша версия игры \"%s\" отклонена модератором @%s. Причина: %s",
                    gameName,
                    moderator.getUsername(),
                    reason);
        }

        var devApp = verdict.getDevApplication();
        if (devApp != null) {
            recipient = devApp.getUser();
            notificationText = String.format(
                    "Ваша заявка на статус разработчика отклонена модератором @%s. Причина: %s",
                    moderator.getUsername(),
                    reason);
        }

        var review = verdict.getReview();
        if (review != null) {
            recipient = review.getUser();
            gameName = review.getGame().getName();
            notificationText = String.format(
                    "Ваш отзыв на игру \"%s\" скрыт навсегда модератором @%s. Причина: %s",
                    gameName,
                    moderator.getUsername(),
                    reason);
        }

        verdict.setApproved(false);
        verdict.setReason(reason);
        verdict.setModerator(moderator);
        moderationVerdictRepository.save(verdict);

        if (recipient != null && notificationText != null) {
            createNotification(recipient, notificationText, Notification.Type.moderation);
        }
    }

    /**
     * Создаёт уведомление для пользователя о результате модерации.
     *
     * <p>
     * Уведомление создаётся с текущим временем и привязывается
     * к указанному получателю.
     * </p>
     *
     * @param recipient получатель уведомления. Не должен быть null.
     * @param text      текст уведомления. Не должен быть null или пустым.
     * @param type      тип уведомления.
     *                  Для модерации используется
     *                  {@link Notification.Type#moderation}.
     */
    private void createNotification(User recipient, String text, Notification.Type type) {
        Notification notification = new Notification();
        notification.setCreatedAt(Instant.now());
        notification.setType(type);
        notification.setText(text);

        notification.setUsers(new HashSet<>());
        notification.getUsers().add(recipient);

        notificationRepository.save(notification);
    }
}