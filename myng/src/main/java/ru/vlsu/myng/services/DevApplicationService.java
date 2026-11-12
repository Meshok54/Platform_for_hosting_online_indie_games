package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import ru.vlsu.myng.entities.DevApplication;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.DevApplicationRepository;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Сервис для работы с заявками на получение роли разработчика.<br>
 * <br>
 * Обеспечивает бизнес-логику для управления заявками:<br>
 * - сохранение новой заявки от пользователя;<br>
 * - поиск всех заявок, поданных указанным пользователем.<br>
 * <br>
 * Используется в следующих сценариях:<br>
 * - пользователь подаёт заявку на становление разработчиком;<br>
 * - модератор просматривает список заявок конкретного пользователя;<br>
 * - администратор анализирует историю заявок пользователя.<br>
 * <br>
 * Является прослойкой между контроллерами и
 * {@link ru.vlsu.myng.repositories.DevApplicationRepository},
 * инкапсулируя прямые операции с базой данных.
 */
@Service
@RequiredArgsConstructor
public class DevApplicationService {
    private final DevApplicationRepository repo;
    private final ModerationVerdictRepository verdictRepository;

    /**
     * Сохраняет заявку на становление разработчиком в базе данных.
     *
     * <p>
     * Если у заявки уже есть id, она будет обновлена.
     * Если id равен null, будет создана новая запись.
     * </p>
     *
     * <p>
     * Ограничения целостности:
     * </p>
     * <ul>
     *   <li>один пользователь может иметь только одну активную заявку
     *       (ограничение на уровне БД — уникальный внешний ключ);</li>
     *   <li>все поля заявки, отмеченные как обязательные,
     *       должны быть заполнены.</li>
     * </ul>
     *
     * @param app заявка для сохранения. Не должна быть null.
     *
     * @return сохранённая заявка с присвоенным id
     *         (если создавалась новая).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         если:
     *         <ul>
     *           <li>пользователь уже имеет заявку на рассмотрении;</li>
     *           <li>нарушены другие ограничения целостности.</li>
     *         </ul>
     * @throws org.springframework.dao.DataAccessException
     *         при ошибке доступа к базе данных
     */
    public DevApplication save(DevApplication app) {
        return repo.save(app);
    }

    /**
     * Возвращает список всех заявок, поданных указанным пользователем.
     *
     * <p>
     * Пользователь может подавать несколько заявок за всю историю,
     * если предыдущие были отклонены или отозваны.
     * </p>
     *
     * @param u пользователь, чьи заявки требуется получить.
     *          Не должен быть null.
     *
     * @return список заявок пользователя. Никогда не возвращает null.
     *         Может быть пустым, если пользователь ещё не подавал заявок.
     *
     * @throws IllegalArgumentException если пользователь равен null
     * @throws org.springframework.dao.DataAccessException
     *         при ошибке доступа к базе данных
     */
    public List<DevApplication> findByUser(User u) {
        return repo.findByUser(u);
    }

    /**
     * Принимает заявку пользователя на получение роли разработчика
     * и отправляет её на модерацию.
     *
     * @param user           пользователь, подающий заявку. Не должен быть null.
     * @param githubUsername имя пользователя на GitHub.
     * @param text           текст заявки.
     *
     * @return сохранённая заявка.
     *
     * @throws IllegalStateException если у пользователя уже есть права разработчика
     *                               (или выше) либо его заявка уже на рассмотрении
     */
    @Transactional
    public DevApplication submit(User user, String githubUsername, String text) {
        if (user.getRole() != User.Role.user) {
            throw new IllegalStateException("У вас уже есть права разработчика");
        }

        // отдельный запрос на каждую заявку (N+1), но заявок у пользователя единицы
        boolean hasPending = repo.findByUser(user).stream()
                .map(verdictRepository::findByDevApplication)
                .anyMatch(verdict -> verdict.isPresent() && verdict.get().getApproved() == null);

        if (hasPending) {
            throw new IllegalStateException("Ваша заявка уже на рассмотрении");
        }

        DevApplication app = new DevApplication();
        app.setUser(user);
        app.setCreatedAt(Instant.now());
        app.setGithubUsername(githubUsername);
        app.setText(text);
        repo.save(app);

        ModerationVerdict verdict = new ModerationVerdict();
        verdict.setDevApplication(app);
        verdictRepository.save(verdict);

        return app;
    }
}