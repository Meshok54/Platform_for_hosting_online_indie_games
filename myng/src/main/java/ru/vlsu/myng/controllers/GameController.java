package ru.vlsu.myng.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import ru.vlsu.myng.dto.*;
import ru.vlsu.myng.services.*;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.User;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameVersionService gameVersionService;
    private final UserService userService;
    private final ReviewService reviewService;
    private final CollectionService collectionService;
    private final GithubService githubService;

    @GetMapping("/{id}")
    public String getGamePage(@PathVariable Integer id, Model model, Principal principal) {
        try {
            GamePageDTO game = gameService.getGamePageData(id);
            model.addAttribute("game", game);

            gameService.incrementGameTotalViews(id);

            User currentUser = null;
            boolean isAuthenticated = false;
            List<CollectionDTO> userCollections = null;

            if (principal != null) {
                String email = principal.getName();
                currentUser = userService.findByEmail(email);
                isAuthenticated = currentUser != null;

                if (isAuthenticated) {
                    userCollections = collectionService.findAllByUserGameNotIn(currentUser.getId(), game.getId());
                }
            }

            boolean isDeveloper = isAuthenticated &&
                    currentUser != null &&
                    game.getDeveloper() != null &&
                    currentUser.getId().equals(game.getDeveloper().getId());

            boolean hasUserReviewed = false;
            if (isAuthenticated && currentUser != null) {
                Game gameEntity = gameService.getGameById(id);
                hasUserReviewed = reviewService.hasUserReviewedGame(gameEntity, currentUser);
            }

            model.addAttribute("isAuthenticated", isAuthenticated);
            model.addAttribute("userCollections", userCollections);
            model.addAttribute("isDeveloper", isDeveloper);
            model.addAttribute("hasUserReviewed", hasUserReviewed);
            model.addAttribute("currentUserId", currentUser != null ? currentUser.getId() : null);

            return "game";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/";
        }
    }

    @PostMapping("/{id}/edit")
    @ResponseBody
    public ResponseEntity<?> editGame(@PathVariable Integer id,
            @ModelAttribute GameEditRequestDTO request,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            User currentUser = userService.findByEmail(principal.getName());
            Game game = gameService.getGameById(id);

            if (game.getDeveloper() == null || !game.getDeveloper().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы не являетесь разработчиком этой игры");
            }

            gameService.updateGame(id, request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Не удалось обновить игру: " + e.getMessage());
        }
    }

    @GetMapping("/developer/{userId}")
    public String getDeveloperGames(@PathVariable Integer userId, Model model) {
        var user = userService.findById(userId);
        List<MyGame> games = gameService.getGamesForUser(user);
        model.addAttribute("mygames", games);
        return "fragments/my_games :: myGamesFragment";
    }

    @PostMapping("/publish")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> publishGame(
            @Valid @ModelAttribute PublishGameRequest request) {

        gameService.publishGame(request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Game published successfully"));
    }

    @PostMapping("/publish/gamever")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> publishGameVersion(
            @Valid @ModelAttribute PublishGameVersionRequest request,
            Principal principal) {

        User currentUser = userService.findByEmail(principal.getName());
        gameVersionService.publishGameVersion(request, currentUser);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Game published successfully"));
    }

    @PostMapping("/{gameId}/versions/{versionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteGameVersion(
            @PathVariable Integer gameId,
            @PathVariable Integer versionId,
            Principal principal) {

        // ошибки (нет версии, чужая игра) обрабатывает GlobalExceptionHandler
        User currentUser = userService.findByEmail(principal.getName());
        gameVersionService.deleteGameVersion(gameId, versionId, currentUser);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Версия успешно удалена"));
    }

    @GetMapping("/repo-tree")
    public String getRepoTreeFragment(
            @RequestParam String repoLink,
            @RequestParam String commitHash,
            Model model
    ) {

        model.addAttribute("repoLink", repoLink);
        model.addAttribute("commitHash", commitHash);

        return "fragments/repo_tree :: repoTreeFragment";
    }

    @GetMapping("/repo-tree-json")
    @ResponseBody
    public String getRepoTree(  @RequestParam String owner,
                                @RequestParam String repo,
                                @RequestParam String commit) {
        return githubService.getRepositoryTree(owner, repo, commit).block();
    }
}