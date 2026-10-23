package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import ru.vlsu.myng.dto.CreateCollectionDto;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.entities.Collection;
import ru.vlsu.myng.services.CollectionService;
import ru.vlsu.myng.services.UserService;
import ru.vlsu.myng.dto.CollectionName;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@RequestMapping("/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final UserService userService;

    @GetMapping("/user/{userId}")
    public String userCollections(@PathVariable Integer userId, Model model) {

        model.addAttribute("user", userService.findById(userId));
        model.addAttribute("collections", collectionService.getUserCollections(userId));

        return "fragments/collections :: collectionsFragment";
    }

    @GetMapping("/{collectionId}")
    public String collectionDetails(@PathVariable Integer collectionId, Model model) {

        Collection collection = collectionService.getCollection(collectionId);

        model.addAttribute("collection", collection);
        model.addAttribute("games", collectionService.getCollectionGames(collectionId));

        return "fragments/collection_games :: gamesFragment";
    }

    @PostMapping("/create")
    @ResponseBody
    public CreateCollectionDto createCollection(
            @ModelAttribute CreateCollectionDto dto,
            Principal principal) {

        // владелец — всегда текущий пользователь, userId из запроса не используется
        User currentUser = userService.findByEmail(principal.getName());
        Collection collection = collectionService.createCollection(currentUser, dto.getName());

        CreateCollectionDto res = new CreateCollectionDto();
        res.setUserId(collection.getId());
        res.setName(collection.getName());

        return res;
    }

    @PostMapping("/delete/{collectionId}")
    @ResponseBody
    public void deleteCollection(@PathVariable Integer collectionId, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        collectionService.deleteCollection(collectionId, currentUser);
    }

    @PostMapping("/{id}/edit")
    public ResponseEntity<?> editCollectionName(
            @PathVariable Integer id,
            @RequestBody CollectionName dto,
            Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        collectionService.renameCollection(id, dto.getName(), currentUser);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{collectionId}/add-game/{gameId}")
    public ResponseEntity<?> addGame(@PathVariable Integer collectionId,
            @PathVariable Integer gameId,
            Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        collectionService.addGameToCollection(collectionId, gameId, currentUser);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{collectionId}/remove-game/{gameId}")
    @ResponseBody
    public void removeGameFromCollection(@PathVariable Integer collectionId,
                                         @PathVariable Integer gameId,
                                         Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        collectionService.removeGameFromCollection(collectionId, gameId, currentUser);
    }
}