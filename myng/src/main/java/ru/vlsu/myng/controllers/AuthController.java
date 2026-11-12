package ru.vlsu.myng.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.vlsu.myng.dto.UserRegistrationDto;
import ru.vlsu.myng.services.UserService;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/auth")
    public String authPage(Model model) {
        if (!model.containsAttribute("userRegistrationDto")) {
            model.addAttribute("userRegistrationDto", new UserRegistrationDto());
        }
        return "auth";
    }

    @PostMapping("/auth/register")
    public String registerUser(
            @Valid @ModelAttribute UserRegistrationDto registrationDto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        // @Valid включает проверки из UserRegistrationDto (@Email, @NotBlank, @Size):
        // без него аннотации в DTO не работали и email не проверялся вообще
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .findFirst()
                    .orElse("Проверьте правильность заполнения формы");

            return backToForm(registrationDto, message, redirectAttributes);
        }

        try {
            userService.registerNewUser(registrationDto);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Регистрация успешна! Теперь можете войти.");
            return "redirect:/auth?success";

        } catch (IllegalArgumentException e) {
            // Только ошибки проверки данных: их текст написан для пользователя.
            // Остальные исключения (например, ошибки БД) уходят в GlobalExceptionHandler,
            // чтобы их внутренний текст не попадал на страницу
            return backToForm(registrationDto, e.getMessage(), redirectAttributes);
        }
    }

    private String backToForm(UserRegistrationDto registrationDto, String message,
            RedirectAttributes redirectAttributes) {
        // пароль не возвращаем в форму и не храним в сессии
        registrationDto.setPassword(null);
        registrationDto.setConfirmPassword(null);

        redirectAttributes.addFlashAttribute("errorMessage", message);
        redirectAttributes.addFlashAttribute("userRegistrationDto", registrationDto);
        return "redirect:/auth?error";
    }
}