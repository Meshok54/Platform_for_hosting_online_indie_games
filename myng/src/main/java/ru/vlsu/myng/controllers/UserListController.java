package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.vlsu.myng.dto.BanRequest;
import ru.vlsu.myng.dto.ChangeRoleRequest;
import ru.vlsu.myng.dto.UnbanRequest;
import ru.vlsu.myng.dto.WarningRequest;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.services.UserListService;
import ru.vlsu.myng.services.UserService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserListController {

    private final UserListService userListService;
    private final UserService userService;

    @GetMapping("/user-list")
    public String userListPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("registeredAt").descending());

        User.Role roleEnum = null;
        if (role != null && !role.isEmpty()) {
            try {
                roleEnum = User.Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                log.warn("Некорректное значение фильтра роли: {}", role);
            }
        }

        UserListService.UserListData data = userListService.getUserListWithBannedStatus(pageable, search, roleEnum,
                status);

        model.addAttribute("users", data.getUsers());
        model.addAttribute("bannedMap", data.getBannedMap());

        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", data.getUsers().getTotalPages());
        model.addAttribute("totalElements", data.getUsers().getTotalElements());

        model.addAttribute("searchFilter", search);
        model.addAttribute("roleFilter", role);
        model.addAttribute("statusFilter", status);

        int startPage = Math.max(0, page - 2);
        int endPage = Math.min(page + 2, data.getUsers().getTotalPages() - 1);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        return "user_list";
    }

    /**
     * Блокировка пользователя (AJAX запрос).
     * Возвращает JSON с результатом.
     */
    @PostMapping("/user-list/ban")
    @ResponseBody
    public Map<String, Object> banUser(@RequestBody BanRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        Map<String, Object> response = new HashMap<>();

        try {
            User moderator = userService.findByEmail(currentUser.getUsername());

            userListService.banUser(
                    request.getUserId(),
                    request.getReason(),
                    request.isPermanent() ? null : request.getDurationHours(),
                    moderator);

            response.put("success", true);
            response.put("message", "Пользователь заблокирован");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Разблокировка пользователя (AJAX запрос).
     */
    @PostMapping("/user-list/unban")
    @ResponseBody
    public Map<String, Object> unbanUser(@RequestBody UnbanRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            userListService.unbanUser(request.getUserId());
            response.put("success", true);
            response.put("message", "Пользователь разблокирован");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Смена роли пользователя (AJAX запрос).
     */
    @PostMapping("/user-list/change-role")
    @ResponseBody
    public Map<String, Object> changeUserRole(@RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        Map<String, Object> response = new HashMap<>();

        try {
            User admin = userService.findByEmail(currentUser.getUsername());

            userListService.changeUserRole(request.getUserId(), request.getNewRole(), admin);

            response.put("success", true);
            response.put("message", "Роль пользователя успешно изменена");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Выдача предупреждения пользователю (AJAX запрос).
     */
    @PostMapping("/user-list/warning")
    @ResponseBody
    public Map<String, Object> issueWarning(@RequestBody WarningRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        Map<String, Object> response = new HashMap<>();

        try {
            User moderator = userService.findByEmail(currentUser.getUsername());

            userListService.issueWarning(request.getUserId(), request.getReason(), moderator);

            response.put("success", true);
            response.put("message", "Предупреждение выдано");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }
}