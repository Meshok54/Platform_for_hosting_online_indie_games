package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vlsu.myng.entities.Notification;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.NotificationRepository;
import ru.vlsu.myng.repositories.UserRepository;
import ru.vlsu.myng.utils.NotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Возвращает список уведомлений, связанных с указанным пользователем.
     *
     * <p>
     * Выполняется загрузка пользователя по идентификатору,
     * после чего извлекаются все уведомления, связанные с ним.
     * </p>
     *
     * @param userId идентификатор пользователя.
     *               Не должен быть null.
     *
     * @return список уведомлений пользователя.
     *         Никогда не возвращает null.
     *         Может возвращать пустой список,
     *         если уведомления отсутствуют.
     *
     * @throws IllegalArgumentException                    если пользователь с указанным id не найден
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    public List<Notification> getByUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return notificationRepository.findByUsersOrderByCreatedAtDesc(user);
    }

    /**
     * Удаляет уведомление у конкретного пользователя.
     *
     * <p>
     * Метод не удаляет уведомление сразу из базы:
     * сначала удаляется связь между пользователем и уведомлением.
     * Если после этого не остаётся ни одного пользователя,
     * уведомление удаляется полностью.
     * </p>
     *
     * @param notificationId идентификатор уведомления.
     *                       Не должен быть null.
     *
     * @param userId идентификатор пользователя.
     *               Не должен быть null.
     *
     * @throws IllegalArgumentException                    если уведомление или пользователь не найдены
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    public void removeNotificationForUser(Integer notificationId, Integer userId) {

        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Уведомление не найдено"));

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        notification.getUsers().remove(user);

        if (notification.getUsers().isEmpty()) {
            notificationRepository.delete(notification);
        } else {
            notificationRepository.save(notification);
        }
    }

    public List<Notification> getByType(Notification.Type type) {
        return notificationRepository.findByType(type);
    }

    public Notification save(Notification notification) {
        return notificationRepository.save(notification);
    }

    public void delete(Integer id) {
        notificationRepository.deleteById(id);
    }
}