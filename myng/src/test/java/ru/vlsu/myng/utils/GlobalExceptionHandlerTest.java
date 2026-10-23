package ru.vlsu.myng.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что {@link GlobalExceptionHandler} отдаёт правильные HTTP-статусы
 * и не раскрывает клиенту текст непредвиденных ошибок.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class FailingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Отзыв не найден");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Можно изменять только свои отзывы");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new IllegalArgumentException("Версия не принадлежит данной игре");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new RuntimeException("Duplicate entry 'vasya' for key 'user.username'");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFound_Returns404WithMessage() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Отзыв не найден"));
    }

    @Test
    void accessDenied_Returns403WithMessage() throws Exception {
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Можно изменять только свои отзывы"));
    }

    @Test
    void businessError_Returns400WithMessage() throws Exception {
        mockMvc.perform(get("/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Версия не принадлежит данной игре"));
    }

    @Test
    void unexpectedError_Returns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("Duplicate entry"))))
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера. Попробуйте позже"));
    }

    @Test
    void pageRequest_GetsErrorPageInsteadOfJson() throws Exception {
        mockMvc.perform(get("/forbidden").accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }
}
