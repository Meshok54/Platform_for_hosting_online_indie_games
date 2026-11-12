package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.services.DevApplicationService;
import ru.vlsu.myng.services.UserService;
import ru.vlsu.myng.dto.DevApplicationDto;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dev-applications")
public class DevApplicationController {

    private final DevApplicationService devApplicationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<?> submitDevApplication(
            @RequestBody DevApplicationDto dto,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principalUser
    ) {
        // проверки (уже разработчик, заявка на рассмотрении) — в DevApplicationService,
        // их ошибки отдаёт GlobalExceptionHandler
        User user = userService.findByEmail(principalUser.getUsername());
        devApplicationService.submit(user, dto.getGithubUsername(), dto.getText());

        return ResponseEntity.ok().build();
    }
}