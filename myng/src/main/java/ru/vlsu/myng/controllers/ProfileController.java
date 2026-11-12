package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import ru.vlsu.myng.dto.MyGame;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.entities.Review;
import ru.vlsu.myng.entities.Collection;
import ru.vlsu.myng.services.GameService;
import ru.vlsu.myng.services.UserService;
import ru.vlsu.myng.repositories.CollectionRepository;
import ru.vlsu.myng.repositories.ReviewRepository;

import org.springframework.security.access.AccessDeniedException;
import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final CollectionRepository collectionRepository;
    private final ReviewRepository reviewRepository;
    private final GameService gameService;

    @GetMapping("/profile")
    public String myProfilePage(Principal principal, Model model) {
        String email = principal.getName();
        User user = userService.findByEmail(email);
        model.addAttribute("user", user);
        List<Review> reviews = reviewRepository.findByUser(user);
        model.addAttribute("reviews", reviews);
        return "profile";
    }

    @GetMapping("/profile/{id}/avatar")
    @ResponseBody
    public byte[] getAvatar(@PathVariable("id") Integer id) {
        User user = userService.findById(id);

        if (user.getProfilePic() != null && user.getProfilePic().length > 0) {
            return user.getProfilePic();
        }

        return new byte[0];
    }

    @PostMapping("/profile/{id}/edit")
    @ResponseBody
    public ResponseEntity<?> editProfile(
            @PathVariable Integer id,
            @RequestParam String username,
            @RequestParam String about,
            @RequestParam(required = false) MultipartFile avatar,
            Principal principal
    ) {
        User currentUser = userService.findByEmail(principal.getName());

        if (!currentUser.getId().equals(id)) {
            throw new AccessDeniedException("Нельзя редактировать чужой профиль");
        }

        // проверки имени, описания и аватара — в UserService, ошибки отдаёт GlobalExceptionHandler
        userService.updateProfile(currentUser, username, about, avatar);

        return ResponseEntity.ok().build();
    }
}