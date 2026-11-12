package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.vlsu.myng.dto.ModerationItem;
import ru.vlsu.myng.services.ModerationLogService;
import ru.vlsu.myng.services.ModerationVerdictService;
import ru.vlsu.myng.utils.PageLimits;

@Controller
@RequiredArgsConstructor
@RequestMapping("/to-moderate")
public class ToModerateController {

        private final ModerationVerdictService moderationVerdictService;
        private final ModerationLogService moderationLogService;

        @GetMapping("")
        public String toModeratePage(

                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,

                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String type,
                        @RequestParam(required = false) String period,
                        @RequestParam(defaultValue = "newest") String sort,

                        Model model) {

                page = PageLimits.page(page);
                size = PageLimits.size(size);

                Sort sorting = sort.equals("oldest")
                                ? JpaSort.unsafe(Sort.Direction.ASC,
                                                "COALESCE(gv.createdAt, da.createdAt, r.createdAt)")
                                : JpaSort.unsafe(Sort.Direction.DESC,
                                                "COALESCE(gv.createdAt, da.createdAt, r.createdAt)");

                Pageable pageable = PageRequest.of(
                                page,
                                size,
                                sorting);

                Page<ModerationItem> moderationPage = moderationLogService.getModerationItems(
                                search,
                                type,
                                "pending",
                                period,
                                pageable);

                model.addAttribute("moderationPage", moderationPage);
                model.addAttribute("moderationItems", moderationPage.getContent());

                return "to_moderate";
        }

        @PostMapping("/approve/{moderationVerdictId}")
        public ResponseEntity<Void> approve(
                        @PathVariable Integer moderationVerdictId,
                        @AuthenticationPrincipal User user) {

                moderationVerdictService.approve(moderationVerdictId, user);

                return ResponseEntity.ok().build();
        }

        @PostMapping("/reject/{moderationVerdictId}")
        public ResponseEntity<Void> reject(
                        @PathVariable Integer moderationVerdictId,
                        @RequestParam String reason,
                        @AuthenticationPrincipal User user) {
                moderationVerdictService.reject(moderationVerdictId, reason, user);

                return ResponseEntity.ok().build();
        }
}