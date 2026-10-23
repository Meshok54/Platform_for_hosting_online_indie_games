package ru.vlsu.myng.utils;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Единая обработка ошибок контроллеров.
 *
 * <p>
 * Клиенту отдаются только сообщения, которые написаны для пользователя
 * (ошибки валидации, «не найдено», «нет доступа», ошибки бизнес-логики).
 * Непредвиденные ошибки пишутся в лог, а клиент получает общий текст,
 * чтобы наружу не попадали SQL-запросы, имена таблиц и т.п.
 * </p>
 *
 * <p>
 * Для fetch-запросов ответ отдаётся в JSON, для обычного перехода
 * по странице — стандартная страница ошибки.
 * </p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR_MESSAGE = "Внутренняя ошибка сервера. Попробуйте позже";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(GithubException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleGithubException(GithubException ex) {

        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getField(), ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({ NotFoundException.class, EntityNotFoundException.class })
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleNotFound(
            RuntimeException ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        return respond(HttpStatus.FORBIDDEN, ex.getMessage(), request, response);
    }

    /**
     * Ошибки бизнес-логики, сообщения которых написаны для пользователя
     * («Версия не принадлежит данной игре», «Verdict already decided» и т.п.).
     */
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            GameDownloadException.class
    })
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            RuntimeException ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request, response);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleGeneralError(
            Exception ex,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // Стандартные ошибки Spring MVC (нет такого ресурса, не тот HTTP-метод,
        // не хватает параметра и т.п.) уже знают свой HTTP-статус
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String message = resolved != null ? resolved.getReasonPhrase() : "Ошибка запроса";

            return respond(status, message, request, response);
        }

        log.error("Необработанная ошибка при запросе {} {}",
                request.getMethod(), request.getRequestURI(), ex);

        return respond(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request, response);
    }

    private ResponseEntity<Map<String, Object>> respond(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (isPageRequest(request)) {
            // Spring Boot перенаправит на /error и покажет страницу ошибки
            response.sendError(status.value());
            return null;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }

    /**
     * Обычный переход по странице браузер делает с {@code Accept: text/html},
     * а fetch по умолчанию отправляет {@code Accept: *}{@code /*}.
     */
    private boolean isPageRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }
}
