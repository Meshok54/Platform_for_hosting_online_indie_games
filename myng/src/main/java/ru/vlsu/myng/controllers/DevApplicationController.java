package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vlsu.myng.entities.DevApplication;
import ru.vlsu.myng.entities.ModerationVerdict;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.services.DevApplicationService;
import ru.vlsu.myng.services.ModerationVerdictService;
import ru.vlsu.myng.services.UserService;
import ru.vlsu.myng.dto.DevApplicationDto;

import java.time.Instant;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dev-applications")
public class DevApplicationController {

    private final DevApplicationService devApplicationService;
    private final ModerationVerdictService moderationVerdictService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> submitDevApplication(
            @RequestBody DevApplicationDto dto,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principalUser
    ) {
        // если у пользователя уже есть заявка на рассмотрении, новую не принимаем
        User u = userService.findByEmail(principalUser.getUsername());
        List<DevApplication> applist = devApplicationService.findByUser(u);
        if (!applist.isEmpty())
        {
            for (var a : applist)
            {
                // отдельный запрос на каждую заявку (N+1), но заявок у пользователя единицы
                var modverdict = moderationVerdictService.findByDevApplication(a);
                if (modverdict.isPresent() && modverdict.get().getApproved() == null)
                {
                    return ResponseEntity.badRequest().build();
                }
            }
        }

        User user = userService.findByEmail(principalUser.getUsername());

        DevApplication app = new DevApplication();
        app.setUser(user);
        app.setCreatedAt(Instant.now());
        app.setGithubUsername(dto.getGithubUsername());
        app.setText(dto.getText());

        devApplicationService.save(app);

        ModerationVerdict verdict = new ModerationVerdict();
        verdict.setDevApplication(app);
        verdict.setApproved(null); // waiting for moderation
        verdict.setModerator(null); // not assigned yet
        verdict.setReason(null);
        verdict.setGameVersion(null);
        verdict.setReview(null);

        moderationVerdictService.save(verdict);

        return ResponseEntity.ok().build();
    }
}