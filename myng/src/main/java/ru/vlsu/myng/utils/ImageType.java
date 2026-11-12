package ru.vlsu.myng.utils;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Определяет формат изображения по его содержимому (сигнатуре файла).
 *
 * <p>
 * Заголовку {@code Content-Type} и расширению файла доверять нельзя: их задаёт
 * клиент. Под видом картинки можно загрузить HTML-страницу со скриптом,
 * поэтому тип определяется по первым байтам самого файла, и принимаются
 * только растровые форматы (SVG не разрешён — он может содержать скрипты).
 * </p>
 */
public final class ImageType {

    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private ImageType() {
    }

    /**
     * @param data содержимое файла.
     *
     * @return тип изображения (PNG, JPEG, GIF или WebP) либо {@code null},
     *         если файл не является изображением одного из этих форматов.
     */
    public static MediaType detect(byte[] data) {
        if (data == null) {
            return null;
        }

        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return MediaType.IMAGE_PNG;
        }

        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return MediaType.IMAGE_JPEG;
        }

        if (hasText(data, 0, "GIF87a") || hasText(data, 0, "GIF89a")) {
            return MediaType.IMAGE_GIF;
        }

        if (hasText(data, 0, "RIFF") && hasText(data, 8, "WEBP")) {
            return IMAGE_WEBP;
        }

        return null;
    }

    /**
     * Читает загруженный файл и проверяет, что это изображение.
     *
     * @param file         загруженный файл. Не должен быть null или пустым.
     * @param errorMessage текст ошибки, если файл не является изображением.
     *
     * @return содержимое файла.
     *
     * @throws IllegalArgumentException если файл не является изображением
     * @throws IllegalStateException    если файл не удалось прочитать
     */
    public static byte[] readImage(MultipartFile file, String errorMessage) {
        byte[] data;

        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать загруженное изображение", e);
        }

        if (detect(data) == null) {
            throw new IllegalArgumentException(errorMessage);
        }

        return data;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasText(byte[] data, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);

        if (data.length < offset + expected.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }

        return true;
    }
}
