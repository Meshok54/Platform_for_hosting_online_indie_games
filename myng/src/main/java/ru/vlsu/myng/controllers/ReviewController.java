package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import ru.vlsu.myng.dto.ReviewUpdate;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.User;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.vlsu.myng.services.GameService;
import ru.vlsu.myng.services.ReviewService;
import ru.vlsu.myng.services.UserService;

@Controller
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;
    private final GameService gameService;

    @GetMapping("/user/{userId}")
    public String getUserReviews(@PathVariable Integer userId, Model model) {
        model.addAttribute("reviews", reviewService.getByUser(userId));
        return "fragments/reviews :: reviewsFragment";
    }

    @PostMapping("/update/{id}")
    @ResponseBody
    public void updateReview(@PathVariable Integer id,
            @RequestBody ReviewUpdate dto,
            Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        reviewService.updateReview(id, dto, currentUser);
    }

    @PostMapping("/delete/{id}")
    @ResponseBody
    public void deleteReview(@PathVariable Integer id, Principal principal) {
        User currentUser = userService.findByEmail(principal.getName());
        reviewService.deleteOwnReview(id, currentUser);
    }

    @PostMapping("/game/{id}")
    public String addReview(@PathVariable Integer id,
            @RequestParam Byte rating,
            @RequestParam String text,
            @RequestParam(required = false) String gameId,
            RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userService.getCurrentUser();
            Game game = gameService.getGameById(id);

            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "Необходимо авторизоваться");
                return "redirect:/auth";
            }

            if (game.getDeveloper().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("error", "Разработчик не может оставлять отзывы к своей игре");
                return "redirect:/games/" + id;
            }

            if (reviewService.hasUserReviewedGame(game, currentUser)) {
                redirectAttributes.addFlashAttribute("error", "Вы уже оставили отзыв к этой игре");
                return "redirect:/games/" + id;
            }

            if (rating < 1 || rating > 5) {
                redirectAttributes.addFlashAttribute("error", "Некорректная оценка");
                return "redirect:/games/" + id;
            }

            if (text == null || text.trim().length() < 10) {
                redirectAttributes.addFlashAttribute("error", "Текст отзыва должен содержать минимум 10 символов");
                return "redirect:/games/" + id;
            }

            reviewService.createReview(game, currentUser, rating, text);
            redirectAttributes.addFlashAttribute("success", "Отзыв успешно добавлен!");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при добавлении отзыва: " + e.getMessage());
        }

        return "redirect:/games/" + id;
    }

    @GetMapping("more/game/{id}")
    public String getMoreReviews(@PathVariable Integer id,
            @RequestParam(defaultValue = "1") int page,
            Model model,
            Principal principal) {
        Game game = gameService.getGameById(id);
        List<Review> moreReviews = reviewService.findByGameOrderByCreatedAtDesc(
                game,
                PageRequest.of(page, 5));

        User currentUser = (principal != null) ? userService.findByEmail(principal.getName()) : null;
        model.addAttribute("currentUserId", currentUser != null ? currentUser.getId() : null);
        model.addAttribute("isAuthenticated", principal != null);
        model.addAttribute("reviews", moreReviews);

        return "fragments/review_items :: reviewList";
    }

    @PostMapping("/{reviewId}/report")
    @ResponseBody
    public ResponseEntity<?> reportReview(@PathVariable Integer reviewId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Нужна авторизация");
        }

        User currentUser = userService.findByEmail(principal.getName());
        Review review = reviewService.getReviewById(reviewId);

        if (review.getUser().getId().equals(currentUser.getId())) {
            return ResponseEntity.badRequest().body("Нельзя жаловаться на свой отзыв");
        }

        reviewService.incrementReportCount(reviewId);

        return ResponseEntity.ok().build();
    }
}