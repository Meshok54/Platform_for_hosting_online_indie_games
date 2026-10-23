package ru.vlsu.myng.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.vlsu.myng.dto.CollectionDTO;
import ru.vlsu.myng.entities.Collection;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.repositories.CollectionRepository;
import ru.vlsu.myng.repositories.GameRepository;
import ru.vlsu.myng.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private CollectionService collectionService;

    private User user;
    private Game game;
    private Collection collection;

    @BeforeEach
    void setUp() {

        user = new User();
        user.setId(1);

        game = new Game();
        game.setId(10);

        collection = new Collection();
        collection.setId(100);
        collection.setName("Favorites");
        collection.setUser(user);
        collection.setGames(Set.of(game));
    }

    @Test
    void shouldReturnUserCollections() {

        when(userRepository.findById(1))
                .thenReturn(Optional.of(user));

        when(collectionRepository.findByUser(user))
                .thenReturn(List.of(collection));

        List<Collection> result =
                collectionService.getUserCollections(1);

        assertEquals(1, result.size());
        assertEquals("Favorites", result.get(0).getName());

        verify(userRepository).findById(1);
        verify(collectionRepository).findByUser(user);
    }

    @Test
    void shouldThrowWhenUserNotFound() {

        when(userRepository.findById(1))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collectionService.getUserCollections(1)
        );

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void shouldCreateCollection() {
        when(collectionRepository.save(any(Collection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Collection created =
                collectionService.createCollection(user, "New Collection");

        assertEquals("New Collection", created.getName());
        assertEquals(user, created.getUser());

        verify(collectionRepository).save(any(Collection.class));
    }

    @Test
    void shouldDeleteCollection() {

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        collectionService.deleteCollection(100, user);

        verify(collectionRepository).delete(collection);
    }

    @Test
    void shouldAddGameToCollection() {

        collection.setGames(new java.util.HashSet<>());

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        when(gameRepository.findById(10))
                .thenReturn(Optional.of(game));

        collectionService.addGameToCollection(100, 10, user);

        assertTrue(collection.getGames().contains(game));

        verify(collectionRepository).save(collection);
    }

    @Test
    void shouldRemoveGameFromCollection() {

        collection.setGames(new java.util.HashSet<>(Set.of(game)));

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        when(gameRepository.findById(10))
                .thenReturn(Optional.of(game));

        collectionService.removeGameFromCollection(100, 10, user);

        assertTrue(collection.getGames().isEmpty());

        verify(collectionRepository).save(collection);
    }

    @Test
    void shouldReturnCollectionGames() {

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        List<Game> games =
                collectionService.getCollectionGames(100);

        assertEquals(1, games.size());
        assertEquals(10, games.get(0).getId());
    }

    @Test
    void shouldReturnCollectionDTOs() {

        when(collectionRepository.findAllByUserIdAndGameNotPresent(1, 10))
                .thenReturn(List.of(collection));

        List<CollectionDTO> result =
                collectionService.findAllByUserGameNotIn(1, 10);

        assertEquals(1, result.size());
        assertEquals(collection.getId(), result.get(0).getId());
        assertEquals(collection.getName(), result.get(0).getName());
    }

    @Test
    void shouldFindCollectionById() {

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        Collection result = collectionService.findById(100);

        assertEquals(100, result.getId());
    }

    @Test
    void shouldThrowWhenCollectionNotFound() {

        when(collectionRepository.findById(999))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> collectionService.findById(999)
        );

        assertEquals("Collection not found: 999", ex.getMessage());
    }

    // ==========================================
    // Проверка владельца коллекции
    // ==========================================

    @Test
    void shouldNotDeleteForeignCollection() {
        User otherUser = new User();
        otherUser.setId(2);

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        assertThrows(
                AccessDeniedException.class,
                () -> collectionService.deleteCollection(100, otherUser)
        );

        verify(collectionRepository, never()).delete(any());
    }

    @Test
    void shouldNotAddGameToForeignCollection() {
        User otherUser = new User();
        otherUser.setId(2);

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        assertThrows(
                AccessDeniedException.class,
                () -> collectionService.addGameToCollection(100, 10, otherUser)
        );

        verify(collectionRepository, never()).save(any());
    }

    @Test
    void shouldNotRenameForeignCollection() {
        User otherUser = new User();
        otherUser.setId(2);

        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        assertThrows(
                AccessDeniedException.class,
                () -> collectionService.renameCollection(100, "Hacked", otherUser)
        );

        assertEquals("Favorites", collection.getName());
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void shouldRenameOwnCollection() {
        when(collectionRepository.findById(100))
                .thenReturn(Optional.of(collection));

        collectionService.renameCollection(100, "Renamed", user);

        assertEquals("Renamed", collection.getName());
        verify(collectionRepository).save(collection);
    }
}
