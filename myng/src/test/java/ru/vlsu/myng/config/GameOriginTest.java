package ru.vlsu.myng.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет изоляцию игр: файлы игр отдаются только с порта игр,
 * а страницы сайта — только с основного порта.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameOriginTest {

    private static final int GAMES_PORT = 8082;

    private static final String GAME_FILE = "/static/gamefiles/game_1/ver_1/index.html";

    private static Path storageDir;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void createGameFile() throws IOException {
        storageDir = Files.createTempDirectory("myng-storage-");
        Path gameDir = storageDir.resolve("gamefiles/game_1/ver_1");
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve("index.html"), "<html><body>test game</body></html>");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.path", () -> storageDir.toString().replace('\\', '/'));
        registry.add("app.games.port", () -> GAMES_PORT);
    }

    /** Имитирует запрос, пришедший на порт игр. */
    private static RequestPostProcessor onGamesPort() {
        return request -> {
            request.setLocalPort(GAMES_PORT);
            return request;
        };
    }

    // ---------- Порт игр ----------

    @Test
    void gamesPort_ServesGameFiles() throws Exception {
        mockMvc.perform(get(GAME_FILE).with(onGamesPort()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test game")));
    }

    @Test
    void gamesPort_AllowsEmbeddingOnlyFromSitePages() throws Exception {
        mockMvc.perform(get(GAME_FILE).with(onGamesPort()))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("frame-ancestors http://localhost:8080")))
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void gamesPort_ServesStoragePatch() throws Exception {
        mockMvc.perform(get("/__myng-storage-patch.js").with(onGamesPort()))
                .andExpect(status().isOk());
    }

    @Test
    void gamesPort_DoesNotServeSitePagesOrApi() throws Exception {
        mockMvc.perform(get("/catalog").with(onGamesPort()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/auth").with(onGamesPort()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/reviews/delete/1").with(csrf()).with(onGamesPort()))
                .andExpect(status().isNotFound());
    }

    // ---------- Основной порт ----------

    @Test
    void sitePort_DoesNotServeGameFiles() throws Exception {
        mockMvc.perform(get(GAME_FILE))
                .andExpect(status().isNotFound());
    }

    @Test
    void sitePort_DoesNotServeGameFiles_WithEncodedPath() throws Exception {
        mockMvc.perform(get("/static/%67amefiles/game_1/ver_1/index.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sitePort_StillServesSitePages() throws Exception {
        mockMvc.perform(get("/catalog"))
                .andExpect(status().isOk());
    }
}
