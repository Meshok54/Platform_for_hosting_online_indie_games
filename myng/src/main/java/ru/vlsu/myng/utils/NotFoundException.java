package ru.vlsu.myng.utils;

/**
 * Запрошенный объект (игра, отзыв, коллекция и т.п.) не найден.
 * Обрабатывается в {@link GlobalExceptionHandler} как HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
