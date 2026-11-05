package ru.vlsu.myng.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет правила доступа из {@link SecurityConfig}:
 * запросы должны отсекаться до того, как попадут в контроллер.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ResultMatcher REDIRECT_TO_LOGIN = redirectedUrlPattern("**/auth");

    private static final ResultMatcher NOT_REDIRECT_TO_LOGIN = result -> {
        String location = result.getResponse().getRedirectedUrl();
        assertFalse(location != null && location.endsWith("/auth"),
                "Публичная страница не должна требовать входа");
    };

    // ---------- Анонимный пользователь ----------

    @Test
    @WithAnonymousUser
    void anonymous_CannotDeleteGameVersion() throws Exception {
        mockMvc.perform(post("/games/1/versions/1").with(csrf()))
                .andExpect(REDIRECT_TO_LOGIN);
    }

    @Test
    @WithAnonymousUser
    void anonymous_CannotPublishGameOrVersion() throws Exception {
        mockMvc.perform(post("/games/publish").with(csrf()))
                .andExpect(REDIRECT_TO_LOGIN);
        mockMvc.perform(post("/games/publish/gamever").with(csrf()))
                .andExpect(REDIRECT_TO_LOGIN);
    }

    @Test
    @WithAnonymousUser
    void anonymous_CannotUseGithubProxy() throws Exception {
        mockMvc.perform(get("/games/repo-tree-json")
                        .param("owner", "o").param("repo", "r").param("commit", "c"))
                .andExpect(REDIRECT_TO_LOGIN);
    }

    @Test
    @WithAnonymousUser
    void anonymous_CanOpenPublicResources() throws Exception {
        mockMvc.perform(get("/__myng-storage-patch.js"))
                .andExpect(status().isOk());
        // несуществующий id: проверяем только правило доступа, а не рендер страницы
        mockMvc.perform(get("/games/999999999"))
                .andExpect(NOT_REDIRECT_TO_LOGIN);
        mockMvc.perform(get("/catalog"))
                .andExpect(NOT_REDIRECT_TO_LOGIN);
    }

    // ---------- Обычный пользователь ----------

    @Test
    @WithMockUser(roles = "USER")
    void user_CannotModerate() throws Exception {
        mockMvc.perform(get("/to-moderate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/to-moderate/approve/1").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/to-moderate/reject/1").with(csrf()).param("reason", "x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/mod-item/reviews/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_CannotPublishOrEditGames() throws Exception {
        mockMvc.perform(post("/games/publish").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/games/1/edit").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/games/1/versions/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void user_CannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/moderation-log"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user-list"))
                .andExpect(status().isForbidden());
    }

    // ---------- Модератор ----------

    @Test
    @WithMockUser(roles = "MOD")
    void moderator_CannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/moderation-log"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user-list/change-role").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------- CSRF ----------

    @Test
    @WithMockUser(roles = "USER")
    void postWithoutCsrfToken_IsRejected() throws Exception {
        mockMvc.perform(post("/reviews/delete/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void postWithInvalidCsrfToken_IsRejected() throws Exception {
        mockMvc.perform(post("/reviews/delete/1").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void pages_ContainCsrfTokenForFormsAndFetch() throws Exception {
        mockMvc.perform(get("/auth"))
                .andExpect(status().isOk())
                // мета-теги для fetch-запросов (layout.html)
                .andExpect(content().string(containsString("<meta name=\"_csrf\" content=\"")))
                .andExpect(content().string(containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"")))
                // скрытое поле в форме входа
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }
}
