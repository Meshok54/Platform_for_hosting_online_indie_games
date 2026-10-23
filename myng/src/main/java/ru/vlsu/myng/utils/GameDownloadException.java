package ru.vlsu.myng.utils;

/**
 * Не удалось скачать или распаковать файлы версии игры из GitHub.
 * Сообщение показывается модератору, поэтому должно быть понятным пользователю.
 */
public class GameDownloadException extends RuntimeException {

    public GameDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
