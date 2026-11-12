package ru.vlsu.myng.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class PublishGameVersionRequest {
    @NotNull
    private Integer gameId;

    @NotBlank(message = "Версия обязательна")
    @Pattern(
            regexp = "^v\\d+\\.\\d+\\.\\d+$",
            message = "Версия должна быть в формате v1.0.0"
    )
    private String gameVerName;

    @NotBlank(message = "Хэш коммита обязателен")
    @Pattern(
            regexp = "^[a-fA-F0-9]{7}$",
            message = "Хэш должен быть 7-значным HEX"
    )
    private String commitHash;

    @NotBlank(message = "Точка входа обязательна")
    @Pattern(
            regexp = "^(?!.*\\.\\.)[^\\\\?#<>\"']+\\.html?$",
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Точка входа — путь к HTML-файлу относительно корня репозитория"
    )
    private String entryPoint;

    @NotBlank(
            message = "Выберите файлы нажатием ЛКМ"
    )
    private String files;

    @Length(min = 10, max = 500, message = "Список изменений должен быть от 10 до 500 символов")
    private String changelog;
}