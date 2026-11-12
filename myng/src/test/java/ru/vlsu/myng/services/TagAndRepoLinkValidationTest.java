package ru.vlsu.myng.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vlsu.myng.entities.Tag;
import ru.vlsu.myng.repositories.TagRepository;
import ru.vlsu.myng.utils.GithubException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Формат тегов и ссылок на репозиторий GitHub.
 */
@ExtendWith(MockitoExtension.class)
class TagAndRepoLinkValidationTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    // ---------- Теги ----------

    @Test
    void findOrCreate_ShouldNormalizeTag() {
        when(tagRepository.findByName("open-world")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tag tag = tagService.findOrCreate("  Open World ");

        assertEquals("open-world", tag.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "шутер", "-abc", "abc-", "a--b", "c++" })
    void findOrCreate_ShouldRejectTagsThatDatabaseWouldReject(String rawTag) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tagService.findOrCreate(rawTag));

        assertTrue(ex.getMessage().contains("латинские буквы"));
        verify(tagRepository, never()).save(any());
    }

    // ---------- Ссылки на GitHub ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/owner/repo",
            "https://github.com/owner/repo/",
            "https://github.com/owner/repo.git",
            "http://github.com/owner/repo",
            "https://www.github.com/owner/repo",
            "  https://github.com/owner/repo  "
    })
    void normalizeRepoUrl_ShouldBringLinksToOneForm(String url) {
        assertEquals("https://github.com/owner/repo", GithubService.normalizeRepoUrl(url));
    }

    @Test
    void normalizeRepoUrl_ShouldKeepDotsInsideRepoName() {
        assertEquals("https://github.com/owner/my.game",
                GithubService.normalizeRepoUrl("https://github.com/owner/my.game.git"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://gitlab.com/owner/repo",
            "https://github.com/owner",
            "https://github.com/owner/repo/tree/main",
            "not a link"
    })
    void normalizeRepoUrl_ShouldRejectNonRepositoryLinks(String url) {
        GithubException ex = assertThrows(GithubException.class, () -> GithubService.normalizeRepoUrl(url));

        assertEquals("repoLink", ex.getField());
    }
}
