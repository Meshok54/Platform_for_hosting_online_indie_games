package ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.ResponseBody;
import ru.vlsu.myng.dto.CatalogGameDTO;
import ru.vlsu.myng.dto.GameFilterDTO;
import ru.vlsu.myng.entities.Game;
import ru.vlsu.myng.services.GameService;
import ru.vlsu.myng.utils.PageLimits;
import ru.vlsu.myng.repositories.TagRepository;

import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class CatalogController {

    private final GameService gameService;
    private final TagRepository tagRepository;

    @GetMapping("/catalog")
    public String catalogPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Game.Genre genre,
            @RequestParam(required = false) Set<String> tags,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            Model model) {

        page = PageLimits.page(page);
        size = PageLimits.size(size);

        GameFilterDTO filter = new GameFilterDTO();
        filter.setSearch(search);
        filter.setGenre(genre);
        filter.setTags(tags);
        filter.setMinRating(minRating);
        filter.setSort(sort);

        Pageable pageable = PageRequest.of(page, size);
        
        Page<CatalogGameDTO> gamesPage = gameService.getFilteredGames(filter, pageable);

        model.addAttribute("pageSize", size);
        model.addAttribute("games", gamesPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", gamesPage.getTotalPages());
        model.addAttribute("totalElements", gamesPage.getTotalElements());
        model.addAttribute("currentSearch", search);
        model.addAttribute("currentGenre", genre);
        model.addAttribute("currentTags", tags != null ? tags : Set.of());
        model.addAttribute("currentMinRating", minRating);
        model.addAttribute("currentSort", sort);
        model.addAttribute("genres", Game.Genre.values());
        
        return "catalog";
    }

    @GetMapping("/catalog/tags/search")
    @ResponseBody
    public List<String> searchTags(
            @RequestParam String query
    ) {
        return tagRepository.searchTags(query, PageRequest.of(0, 20));
    }
}