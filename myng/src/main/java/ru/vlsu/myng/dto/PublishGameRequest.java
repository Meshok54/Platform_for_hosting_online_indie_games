package ru.vlsu.myng.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import ru.vlsu.myng.utils.FileNotEmpty;
import ru.vlsu.myng.utils.MaxFileSize;
import ru.vlsu.myng.utils.UniqueRepo;

@Data
public class PublishGameRequest {

    @NotBlank(message = "Название обязательно")
    @Size(min = 3, max = 100, message = "Название должно быть от 3 до 100 символов")
    private String title;

    @NotBlank(message = "Описание обязательно")
    @Size(min = 10, max = 2000, message = "Описание должно быть от 10 до 2000 символов")
    private String description;

    @NotBlank(message = "Выберите жанр")
    private String genre;

    @NotBlank(message = "Ссылка на репозиторий обязательна")
    @Pattern(
            regexp = "^https?://(www\\.)?github\\.com/[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+/?$",
            message = "Ссылка должна быть в формате https://github.com/user/repo"
    )
    //@UniqueRepo
    private String repoLink;

    @NotBlank(message = "Хэш коммита обязателен")
    @Pattern(
            regexp = "^[a-fA-F0-9]{7}$",
            message = "Хэш должен быть 7-значным HEX"
    )
    private String commitHash;

    @NotBlank(message = "Точка входа обязательна")
    @Pattern(
            regexp = "^[a-zA-Z0-9_\\-./]+\\.html$",
            message = "Укажите путь к HTML-файлу относительно корня репозитория (например: index.html или game/index.html)"
    )
    private String entryPoint;
    
    @Pattern(
            regexp = "^([a-zA-Z0-9_\\-./]+)(,\\s*[a-zA-Z0-9_\\-./]+)*$",
            message = "Введите названия файлов и папок в формате: file.ext, folder, dir/file.ext"
    )
    private String files;

    @NotBlank(message = "Укажите хотя бы один тег")
    @Pattern(
            regexp = "^#([a-zA-Z0-9-]+)(,\\s*#([a-zA-Z0-9-]+))*$",
            message = "Теги должны быть в формате: #tag1, #tag2"
    )
    private String tags;

    @NotBlank(message = "Версия обязательна")
    @Pattern(
            regexp = "^v\\d+\\.\\d+\\.\\d+$",
            message = "Версия должна быть в формате v1.0.0"
    )
    private String gameVer;

    //@FileNotEmpty
    @MaxFileSize(value = 32 * 1024 * 1024, message = "Максимальный размер файла — 32MB")
    private MultipartFile mainPic;
}