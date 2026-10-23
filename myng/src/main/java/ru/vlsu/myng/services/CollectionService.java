package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.vlsu.myng.dto.CollectionDTO;
import ru.vlsu.myng.entities.Collection;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.CollectionRepository;
import ru.vlsu.myng.repositories.GameRepository;
import ru.vlsu.myng.repositories.UserRepository;
import ru.vlsu.myng.utils.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * Сервис для управления коллекциями игр пользователей.<br>
 * <br>
 * Обеспечивает бизнес-логику для работы с коллекциями:<br>
 * - создание и удаление коллекций;<br>
 * - получение списка коллекций пользователя;<br>
 * - добавление и удаление игр из коллекции;<br>
 * - поиск коллекций, в которых отсутствует указанная игра;<br>
 * - преобразование сущностей коллекций в DTO для отображения в UI.<br>
 * <br>
 * Все операции с изменением данных выполняются в транзакциях.
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    /**
     * Возвращает список всех коллекций указанного пользователя.
     *
     * @param userId идентификатор пользователя. Не должен быть null.
     *
     * @return список коллекций пользователя. Никогда не возвращает null.
     *         Может быть пустым, если у пользователя ещё нет коллекций.
     *
     * @throws IllegalArgumentException если пользователь с указанным id не найден
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<Collection> getUserCollections(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ;
        return collectionRepository.findByUser(user);
    }

    /**
     * Возвращает коллекцию по её идентификатору.
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     *
     * @return коллекция с указанным id.
     *
     * @throws IllegalArgumentException если коллекция с указанным id не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Collection getCollection(Integer collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection not found"));
    }

    /**
     * Возвращает список игр, входящих в указанную коллекцию.
     *
     * <p>
     * Возвращает копию списка игр для предотвращения
     * непреднамеренного изменения коллекции через полученный список.
     * </p>
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     *
     * @return список игр в коллекции. Никогда не возвращает null.
     *         Может быть пустым, если в коллекции нет игр.
     *
     * @throws IllegalArgumentException если коллекция с указанным id не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<Game> getCollectionGames(Integer collectionId) {
        return new ArrayList<>(getCollection(collectionId).getGames());
    }

    /**
     * Создаёт новую коллекцию для указанного пользователя.
     *
     * <p>
     * Имя коллекции должно быть уникальным в рамках коллекций пользователя
     * (ограничение на уровне БД).
     * </p>
     *
     * @param owner пользователь-владелец (текущий пользователь). Не должен быть null.
     * @param name  название коллекции. Не должно быть null или пустым.
     *
     * @return созданная коллекция с присвоенным id.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         если коллекция с таким именем уже существует у пользователя
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Collection createCollection(User owner, String name) {

        Collection collection = new Collection();
        collection.setName(name);
        collection.setUser(owner);

        return collectionRepository.save(collection);
    }

    /**
     * Удаляет коллекцию по её идентификатору.
     *
     * <p>
     * При удалении коллекции связанные игры не удаляются —
     * удаляется только связь между коллекцией и играми.
     * </p>
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     * @param currentUser  пользователь, выполняющий удаление.
     *
     * @throws NotFoundException     если коллекция с указанным id не найдена
     * @throws AccessDeniedException если коллекция принадлежит другому пользователю
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public void deleteCollection(Integer collectionId, User currentUser) {
        Collection collection = getOwnCollection(collectionId, currentUser);

        collectionRepository.delete(collection);
    }

    /**
     * Переименовывает коллекцию текущего пользователя.
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     * @param name         новое название. Не должно быть null или пустым.
     * @param currentUser  пользователь, выполняющий изменение.
     *
     * @throws NotFoundException     если коллекция с указанным id не найдена
     * @throws AccessDeniedException если коллекция принадлежит другому пользователю
     */
    @Transactional
    public void renameCollection(Integer collectionId, String name, User currentUser) {
        Collection collection = getOwnCollection(collectionId, currentUser);

        collection.setName(name);
        collectionRepository.save(collection);
    }

    /**
     * Возвращает коллекцию, проверив, что её владелец — текущий пользователь.
     *
     * @throws NotFoundException     если коллекция не найдена
     * @throws AccessDeniedException если коллекция принадлежит другому пользователю
     */
    private Collection getOwnCollection(Integer collectionId, User currentUser) {
        Collection collection = findById(collectionId);

        if (!collection.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Можно изменять только свои коллекции");
        }

        return collection;
    }

    /**
     * Сохраняет или обновляет коллекцию в базе данных.
     *
     * <p>
     * Если коллекция с таким id уже существует, она будет обновлена.
     * Если id равен null, будет создана новая коллекция.
     * </p>
     *
     * @param collection коллекция для сохранения. Не должна быть null.
     *
     * @return сохранённая коллекция с актуальными данными.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public Collection save(Collection collection) {
        return collectionRepository.save(collection);
    }

    /**
     * Находит коллекцию по идентификатору.
     *
     * <p>
     * Отличается от {@link #getCollection(Integer)} типом выбрасываемого
     * исключения и сообщением об ошибке.
     * </p>
     *
     * @param id идентификатор коллекции. Не должен быть null.
     *
     * @return коллекция с указанным id.
     *
     * @throws NotFoundException если коллекция с указанным id не найдена
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public Collection findById(Integer id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Collection not found: " + id));
    }

    /**
     * Возвращает список DTO коллекций пользователя, в которых отсутствует
     * указанная игра.
     *
     * <p>
     * Используется для отображения диалога добавления игры в коллекцию:
     * показывает только те коллекции, в которые игру ещё можно добавить.
     * </p>
     *
     * @param userId идентификатор пользователя. Не должен быть null.
     * @param gameId идентификатор игры. Не должен быть null.
     *
     * @return список DTO коллекций, не содержащих указанную игру.
     *         Никогда не возвращает null.
     *         Может быть пустым, если игра уже во всех коллекциях
     *         или у пользователя нет коллекций.
     *
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    public List<CollectionDTO> findAllByUserGameNotIn(Integer userId, Integer gameId) {
        return collectionRepository.findAllByUserIdAndGameNotPresent(userId, gameId)
                .stream()
                .map(c -> new CollectionDTO(c.getId(), c.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Добавляет игру в указанную коллекцию.
     *
     * <p>
     * Если игра уже присутствует в коллекции,
     * повторного добавления не происходит.
     * </p>
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     * @param gameId       идентификатор игры. Не должен быть null.
     * @param currentUser  пользователь, выполняющий изменение.
     *
     * @throws NotFoundException     если коллекция или игра с указанным id не найдена
     * @throws AccessDeniedException если коллекция принадлежит другому пользователю
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void addGameToCollection(Integer collectionId, Integer gameId, User currentUser) {
        Collection collection = getOwnCollection(collectionId, currentUser);
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Игра не найдена"));

        collection.getGames().add(game);
        collectionRepository.save(collection);
    }

    /**
     * Удаляет игру из указанной коллекции.
     *
     * <p>
     * Если игра не входит в коллекцию, операция завершается без ошибок.
     * </p>
     *
     * @param collectionId идентификатор коллекции. Не должен быть null.
     * @param gameId       идентификатор игры. Не должен быть null.
     * @param currentUser  пользователь, выполняющий изменение.
     *
     * @throws NotFoundException     если коллекция или игра с указанным id не найдена
     * @throws AccessDeniedException если коллекция принадлежит другому пользователю
     * @throws org.springframework.dao.DataAccessException при ошибке доступа к БД
     */
    @Transactional
    public void removeGameFromCollection(Integer collectionId, Integer gameId, User currentUser) {

        Collection collection = getOwnCollection(collectionId, currentUser);

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new NotFoundException("Игра не найдена"));

        collection.getGames().removeIf(g -> g.getId().equals(gameId));

        collectionRepository.save(collection);
    }
}