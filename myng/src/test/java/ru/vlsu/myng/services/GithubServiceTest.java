package ru.vlsu.myng.services;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.GameVersion;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock
class GithubServiceTest {

    @Autowired
    private GithubService githubService;

    @InjectWireMock
    private WireMockServer wireMockServer;

    @TempDir
    Path tempStorageDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("github.token", () -> "mock-test-token");
        registry.add("app.storage.path", () -> System.getProperty("java.io.tmpdir"));
    }

    @Test
    void should_DownloadZip_ExtractFiles_AndInjectScriptsIntoHtml() throws Exception {
        ReflectionTestUtils.setField(githubService, "storagePath", tempStorageDir.toString());
        
        WebClient mockWebClient = WebClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .build();

        GithubService target =
                AopTestUtils.getUltimateTargetObject(githubService);

        ReflectionTestUtils.setField(
                target,
                "webClient",
                mockWebClient
        );
        
        Game game = new Game();
        game.setId(7);
        game.setRepo("https://github.com/my-owner/my-repo");

        GameVersion version = new GameVersion();
        version.setId(14);
        version.setCommitHash("xyz987");
        version.setGame(game);
        version.setFiles("index.html, Route/Route.html");

        // Programmatically generate a dummy ZIP in memory (Mimicking GitHub's layout)
        // GitHub always wraps repo contents inside a root folder (e.g., 'repo-name-commithash/')
        byte[] mockZipBytes = Files.readAllBytes(Path.of("src/test/resources/test-repo.zip"));

        // Stub the WireMock server endpoint
        wireMockServer.stubFor(get(urlEqualTo("/repos/my-owner/my-repo/zipball/xyz987"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/zip")
                        .withBody(mockZipBytes)));
        
        githubService.downloadGameVersion(version);
        
        Path expectedTargetDir = tempStorageDir.resolve("gamefiles")
                .resolve("game_7")
                .resolve("ver_14");

        Path expectedHtmlFile = expectedTargetDir.resolve("index.html");

        Path expectedRouteFile =
                expectedTargetDir.resolve("Route").resolve("Route.html");
        
        assertTrue(Files.exists(expectedHtmlFile), "index.html should be extracted to the target directory");

        assertTrue(
                Files.exists(expectedRouteFile),
                "Route.html should be extracted"
        );
        
        String finalHtml = Files.readString(expectedHtmlFile);

        assertTrue(finalHtml.contains("<script>window.__GAME_ID__=7; window.__GAMEVER_ID__=14</script>"),
                "The game IDs should be injected into the window context variables.");

        assertTrue(finalHtml.contains("<script src=\"/__myng-storage-patch.js\"></script>"),
                "The patch script inclusion reference tag should be present.");
    }

    @Test
    void should_RejectArchive_AndCleanUp_WhenUnpackedSizeExceedsLimit() throws Exception {
        GithubService target =
                AopTestUtils.getUltimateTargetObject(githubService);

        ReflectionTestUtils.setField(target, "storagePath", tempStorageDir.toString());
        ReflectionTestUtils.setField(
                target,
                "webClient",
                WebClient.builder().baseUrl(wireMockServer.baseUrl()).build()
        );

        Object originalLimit = ReflectionTestUtils.getField(target, "maxUnpackedSize");
        ReflectionTestUtils.setField(target, "maxUnpackedSize", 1024L);

        try {
            // 1 МБ одинаковых символов сжимается до пары килобайт — маленькая модель zip-бомбы
            byte[] bombZip = createMockGithubZip(
                    "my-repo-bomb123/index.html",
                    "0".repeat(1024 * 1024)
            );

            wireMockServer.stubFor(get(urlEqualTo("/repos/my-owner/my-repo/zipball/bomb123"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/zip")
                            .withBody(bombZip)));

            Game game = new Game();
            game.setId(8);
            game.setRepo("https://github.com/my-owner/my-repo");

            GameVersion version = new GameVersion();
            version.setId(15);
            version.setCommitHash("bomb123");
            version.setGame(game);
            version.setFiles("index.html");

            assertThrows(RuntimeException.class, () -> githubService.downloadGameVersion(version));

            Path targetDir = tempStorageDir.resolve("gamefiles")
                    .resolve("game_8")
                    .resolve("ver_15");

            assertFalse(Files.exists(targetDir), "Partially extracted files should be removed");
        } finally {
            ReflectionTestUtils.setField(target, "maxUnpackedSize", originalLimit);
        }
    }

    /**
     * Helper utility to create a valid zip stream containing a mock file layout.
     */
    private byte[] createMockGithubZip(String filePath, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(filePath);
            zos.putNextEntry(entry);
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}