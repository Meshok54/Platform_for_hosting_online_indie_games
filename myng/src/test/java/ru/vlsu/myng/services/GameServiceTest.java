package ru.vlsu.myng.services;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vlsu.myng.dto.CatalogGameDTO;
import ru.vlsu.myng.dto.GameFilterDTO;
import ru.vlsu.myng.dto.PublishGameRequest;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.GameVersion;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.Tag;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.GameRepository;
import ru.vlsu.myng.repositories.GameVersionRepository;
import ru.vlsu.myng.repositories.ModerationVerdictRepository;
import ru.vlsu.myng.repositories.TagRepository;
import ru.vlsu.myng.repositories.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.mock.web.MockMultipartFile;

import org.springframework.test.annotation.DirtiesContext;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameServiceTest extends BaseIntegrationTest {

    @Test
    void contextLoads() {
    }

    @Autowired
    private GameService gameService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameVersionRepository gameVersionRepository;

    @Autowired
    private ModerationVerdictRepository moderationVerdictRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private GithubService githubService;

    @MockBean
    private UserService userService;

    @MockBean
    private TagService tagService;

    private User developer;

    @BeforeEach
    void setup() {

        developer = new User();
        developer.setUsername("test-dev");
        developer.setEmail("test-dev@mail.com");
        developer.setPasswordHash("$2a$10$1ouf1UEsDIWxjMhf/LKIq.zzB0.03GxVmtUCDIW42F5mmwYnCvnc.");
        developer.setRole(User.Role.dev);

        userRepository.save(developer);

        when(userService.getCurrentUser())
                .thenReturn(developer);

        doNothing().when(githubService)
                .validateRepoExists(anyString());

        doNothing().when(githubService)
                .validateCommitExists(
                        anyString(),
                        anyString());

        doNothing().when(githubService)
                .validateFilesExistInCommit(
                        anyString(),
                        anyString(),
                        anyString());

        when(tagService.findOrCreate("action"))
                .thenAnswer(invocation -> {

                    Tag tag = new Tag();
                    tag.setName("action");

                    return tagRepository.save(tag);
                });

        when(tagService.findOrCreate("multiplayer"))
                .thenAnswer(invocation -> {

                    Tag tag = new Tag();
                    tag.setName("multiplayer");

                    return tagRepository.save(tag);
                });
    }

    @Test
    void publishGame_shouldPersistEverythingCorrectly() {

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "cover.jpg",
                "image/jpeg",
                // сигнатура JPEG: тип обложки проверяется по содержимому файла
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 });

        PublishGameRequest request = new PublishGameRequest();

        request.setTitle("CyberGame");
        request.setDescription("Very cool game");
        request.setRepoLink("https://github.com/test/game");
        request.setCommitHash("abc1234");
        request.setFiles("game.exe");
        request.setGenre("action");
        request.setTags("#action, #multiplayer");
        request.setGameVer("v1.0.0");
        request.setMainPic(image);
        request.setEntryPoint("index.html");

        gameService.publishGame(request);

        assertThat(gameRepository.findAll()).hasSize(1);

        Game game = gameRepository.findAll().get(0);

        assertThat(game.getName()).isEqualTo("CyberGame");

        assertThat(game.getDeveloper().getUsername())
                .isEqualTo("test-dev");

        assertThat(game.getRepo())
                .isEqualTo("https://github.com/test/game");

        assertThat(game.getTags())
                .extracting(Tag::getName)
                .containsExactlyInAnyOrder(
                        "action",
                        "multiplayer");

        assertThat(game.getImage()).isNotNull();

        assertThat(gameVersionRepository.findAll())
                .hasSize(1);

        GameVersion version =
                gameVersionRepository.findAll().get(0);

        assertThat(version.getCommitHash())
                .isEqualTo("abc1234");

        assertThat(version.getName())
                .isEqualTo("v1.0.0");

        assertThat(version.getGame().getId())
                .isEqualTo(game.getId());

        assertThat(moderationVerdictRepository.findAll())
                .hasSize(1);

        ModerationVerdict verdict =
                moderationVerdictRepository.findAll().get(0);

        assertThat(verdict.getApproved()).isNull();

        assertThat(verdict.getGameVersion().getId())
                .isEqualTo(version.getId());
    }

    @Test
    void getFilteredGames_shouldReturnFilteredCatalogGames() {

        Tag actionTag = new Tag();
        actionTag.setName("action");

        actionTag = tagRepository.save(actionTag);

        Game game = new Game();
        game.setName("DOOM");
        game.setDescr("FPS");
        game.setGenre(Game.Genre.action);
        game.setAverageRating(4.8);
        game.setTotalLaunches(500);
        game.setFirstReleaseDate(Instant.now());
        game.setTags(Set.of(actionTag));
        game.setDeveloper(developer);
        game.setRepo("https://github.com/test/game");

        gameRepository.save(game);

        GameVersion gv = new GameVersion();
        gv.setChangelog("Fixed bugs");
        gv.setFiles("index.html, script.js, style.css");
        gv.setName("v1.2");
        gv.setCommitHash("cafebabe");
        gv.setGame(game);
        gv.setEntryPoint("index.html");
        
        gameVersionRepository.save(gv);
        
        ModerationVerdict gameVerdict = new ModerationVerdict();
        gameVerdict.setModerator(null);
        gameVerdict.setGameVersion(gv);
        gameVerdict.setApproved(true);
        
        moderationVerdictRepository.save(gameVerdict);
        
        GameFilterDTO filter = new GameFilterDTO();
        filter.setSearch("DOOM");
        filter.setGenre(Game.Genre.action);
        filter.setTags(Set.of("action"));
        filter.setMinRating(4.0);
        filter.setSort("oldest");

        Page<CatalogGameDTO> result =
                gameService.getFilteredGames(
                        filter,
                        PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);

        CatalogGameDTO dto =
                result.getContent().get(0);

        assertThat(dto.getName())
                .isEqualTo("DOOM");

        assertThat(dto.getGenre())
                .isEqualTo(Game.Genre.action);

        assertThat(dto.getThemeColor())
                .isEqualTo("from-red-500 to-orange-600");

        assertThat(dto.getTags())
                .contains("action");
    }
}