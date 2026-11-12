package ru.vlsu.myng.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.myng.entities.Tag;
import ru.vlsu.myng.repositories.TagRepository;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    /**
     * Находит тег по имени или создаёт новый, если он отсутствует.
     *
     * <p>
     * Перед поиском имя нормализуется:
     * приводится к нижнему регистру,
     * обрезаются пробелы,
     * пробелы внутри заменяются на дефисы.
     * </p>
     *
     * @param rawName исходное имя тега.
     *               Не должно быть null или пустым.
     *
     * @return найденный или созданный тег.
     *
     * @throws IllegalArgumentException                    если rawName некорректен
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public Tag findOrCreate(String rawName) {
        String name = normalize(rawName);

        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setName(name);
                    return tagRepository.save(tag);
                });
    }

    /**
     * Возвращает тег по имени.
     *
     * <p>
     * Перед поиском имя нормализуется:
     * приводится к нижнему регистру,
     * обрезаются пробелы,
     * пробелы внутри заменяются на дефисы.
     * </p>
     *
     * @param rawName исходное имя тега.
     *               Не должно быть null или пустым.
     *
     * @return найденный тег.
     *
     * @throws IllegalArgumentException                    если тег не найден или имя некорректно
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    public Tag getByName(String rawName) {
        String name = normalize(rawName);

        return tagRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + name));
    }

    /**
     * Разбирает строку тегов и возвращает набор Tag-сущностей.
     *
     * <p>
     * Строка может содержать несколько тегов, разделённых запятыми.
     * Поддерживаются теги с префиксом '#'.
     * </p>
     *
     * <p>
     * Для каждого тега выполняется:
     * <ul>
     *     <li>обрезка пробелов;</li>
     *     <li>удаление символа '#', если он есть;</li>
     *     <li>нормализация имени;</li>
     *     <li>поиск или создание тега в базе.</li>
     * </ul>
     * </p>
     *
     * @param tagsRaw строка с тегами, разделёнными запятыми.
     *                Не должна быть null или пустой.
     *
     * @return множество Tag-сущностей.
     *         Никогда не возвращает null.
     *
     * @throws IllegalArgumentException                    если tagsRaw некорректна
     * @throws org.springframework.dao.DataAccessException
     *                                                     при ошибке доступа к базе данных
     */
    @Transactional
    public Set<Tag> parseTags(String tagsRaw) {
        if (tagsRaw == null || tagsRaw.isBlank()) {
            throw new IllegalArgumentException("Tags cannot be empty");
        }

        return Arrays.stream(tagsRaw.split(","))
                .map(String::trim)
                .map(tag -> tag.startsWith("#") ? tag.substring(1) : tag)
                .map(this::normalize)
                .map(this::findOrCreate)
                .collect(Collectors.toSet());
    }
    
    /**
     * Тот же формат, что проверяет ограничение chk_tag_format в БД.
     */
    private static final Pattern TAG_FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private String normalize(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Тег не может быть пустым");
        }

        String name = tag.trim()
                .toLowerCase()
                .replace(" ", "-");

        // проверяем здесь, иначе тег отклонит БД и пользователь получит ошибку 500
        if (!TAG_FORMAT.matcher(name).matches()) {
            throw new IllegalArgumentException("Тег «" + tag.trim()
                    + "» может содержать только латинские буквы, цифры и дефисы между ними");
        }

        return name;
    }
}