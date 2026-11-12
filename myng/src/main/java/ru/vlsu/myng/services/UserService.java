package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.myng.dto.UserRegistrationDto;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.UserRepository;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

/**
 * Сервис для работы с пользователями.
 * Содержит бизнес-логику регистрации, поиска и управления пользователями.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Совпадает с длиной колонки user.username в БД. */
    public static final int USERNAME_MAX_LENGTH = 20;

    /** Совпадает с длиной колонки user.bio в БД. */
    public static final int BIO_MAX_LENGTH = 255;


    /**
     * Регистрирует нового пользователя в системе.
     *
     * <p>
     * Выполняются проверки:
     * <ul>
     *     <li>подтверждение пароля;</li>
     *     <li>минимальная длина пароля (>= 6 символов);</li>
     *     <li>имя пользователя не пустое и не длиннее {@link #USERNAME_MAX_LENGTH} символов;</li>
     *     <li>уникальность username;</li>
     *     <li>уникальность email.</li>
     * </ul>
     * </p>
     *
     * <p>
     * При успешной регистрации:
     * <ul>
     *     <li>пароль хешируется;</li>
     *     <li>устанавливается роль user;</li>
     *     <li>задаётся дата регистрации;</li>
     *     <li>создаётся пустой профильный аватар.</li>
     * </ul>
     * </p>
     *
     * @param registrationDto DTO с данными регистрации.
     *                        Не должен быть null.
     *
     * @return созданный пользователь.
     *
     * @throws RuntimeException если данные некорректны
     *                          или пользователь/почта уже существуют
     * @throws org.springframework.dao.DataAccessException
     *                          при ошибке доступа к базе данных
     */
    @Transactional
    public User registerNewUser(UserRegistrationDto registrationDto) {
        if (!registrationDto.isPasswordConfirmed()) {
            throw new RuntimeException("Пароли не совпадают");
        }

        if (registrationDto.getPassword() == null || registrationDto.getPassword().length() < 6) {
            throw new RuntimeException("Пароль должен быть не менее 6 символов");
        }

        validateUsername(registrationDto.getUsername());

        if (userRepository.existsByUsername(registrationDto.getUsername())) {
            throw new RuntimeException("Имя пользователя уже занято");
        }

        if (userRepository.existsByEmail(registrationDto.getEmail())) {
            throw new RuntimeException("Email уже используется");
        }

        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());

        user.setPasswordHash(passwordEncoder.encode(registrationDto.getPassword()));

        user.setRegisteredAt(Instant.now());

        user.setRole(User.Role.user);

        user.setProfilePic(new byte[0]);

        return userRepository.save(user);
    }

    /**
     * Поиск пользователя по id.
     */
    public User findById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + id));
    }

    /**
     * Поиск пользователя по username.
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + username));
    }

    /**
     * Поиск пользователя по email.
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
    }

    /**
     * Проверка существования username.
     */
    public boolean isUsernameTaken(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Проверка существования email.
     */
    public boolean isEmailTaken(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Возвращает текущего аутентифицированного пользователя.
     *
     * <p>
     * Извлекает пользователя из Spring Security Context.
     * </p>
     *
     * <p>
     * Поддерживаются только аутентифицированные пользователи
     * с principal типа UserDetails.
     * </p>
     *
     * @return текущий пользователь системы.
     *
     * @throws IllegalStateException если:
     *                                <ul>
     *                                    <li>нет authentication;</li>
     *                                    <li>пользователь не аутентифицирован;</li>
     *                                    <li>principal неизвестного типа;</li>
     *                                    <li>пользователь не найден в базе.</li>
     *                                </ul>
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }

        if (!authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            throw new IllegalStateException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            String email = userDetails.getUsername();

            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("User not found: " + email));
        }

        throw new IllegalStateException("Unknown authentication principal: " + principal);
    }

    /**
     * Сохранение пользователя.
     */
    /**
     * Обновляет профиль пользователя: имя, описание и (необязательно) аватар.
     *
     * @param user     пользователь, чей профиль изменяется. Не должен быть null.
     * @param username новое имя пользователя.
     * @param bio      новое описание профиля. Может быть null.
     * @param avatar   новый аватар. Может быть null или пустым — тогда аватар не меняется.
     *
     * @throws IllegalArgumentException если имя пустое, слишком длинное или занято,
     *                                  описание слишком длинное или аватар не изображение
     * @throws IllegalStateException    если не удалось прочитать файл аватара
     */
    @Transactional
    public User updateProfile(User user, String username, String bio, MultipartFile avatar) {
        String newUsername = username == null ? null : username.trim();
        validateUsername(newUsername);

        if (!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Имя пользователя уже занято");
        }

        String newBio = bio == null ? "" : bio.trim();
        if (newBio.length() > BIO_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Описание профиля не должно превышать " + BIO_MAX_LENGTH + " символов");
        }

        if (avatar != null && !avatar.isEmpty()) {
            String contentType = avatar.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Аватар должен быть изображением");
            }

            try {
                user.setProfilePic(avatar.getBytes());
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось прочитать файл аватара", e);
            }
        }

        user.setUsername(newUsername);
        user.setBio(newBio);

        return userRepository.save(user);
    }

    /**
     * Проверяет имя пользователя: не пустое и помещается в колонку БД.
     *
     * @throws IllegalArgumentException если имя некорректно
     */
    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }

        if (username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Имя пользователя не должно превышать " + USERNAME_MAX_LENGTH + " символов");
        }
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Проверяет, является ли текущий пользователь разработчиком.
     *
     * <p>
     * Если пользователь не аутентифицирован,
     * возвращает false.
     * </p>
     *
     * @return true, если текущий пользователь имеет роль dev;
     *         false в остальных случаях.
     */
    public boolean isCurrentUserDev() {
        try {
            User currentUser = getCurrentUser();
            return currentUser != null && currentUser.getRole() == User.Role.dev;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}