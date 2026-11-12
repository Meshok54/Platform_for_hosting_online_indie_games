package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.vlsu.myng.dto.ModerationItem;
import ru.vlsu.myng.services.ModerationLogService;
import ru.vlsu.myng.utils.PageLimits;


@Controller
@RequiredArgsConstructor
public class ModerationLogController {

    private final ModerationLogService moderationLogService;

    @GetMapping("/moderation-log")
    public String moderationPage(

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,

            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "newest") String sort,

            Model model
    ) {

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
                sorting
        );

        Page<ModerationItem> moderationPage =
            moderationLogService.getModerationItems(
                    search,
                    type,
                    status,
                    period,
                    pageable
            );

        model.addAttribute("moderationPage", moderationPage);
        model.addAttribute("moderationItems", moderationPage.getContent());

        return "moderation_log";
    }
}