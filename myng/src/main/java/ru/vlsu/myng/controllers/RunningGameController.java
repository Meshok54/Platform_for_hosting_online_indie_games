package  ru.vlsu.myng.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.vlsu.myng.entities.GameVersion;
import ru.vlsu.myng.repositories.GameVersionRepository;
import ru.vlsu.myng.services.GameService;
import ru.vlsu.myng.services.GameVersionService;

@Controller
@RequiredArgsConstructor
public class RunningGameController {
    private final GameVersionService gameVersionService;
    private final GameService gameService;

    @GetMapping("/running-game/{versionId}")
    public String runningGamePage(@PathVariable Integer versionId, Model model) {

        GameVersion version = gameVersionService.getGameVersionById(versionId);
        String entryPoint = gameVersionService.resolveEntryPoint(version);

        gameService.incrementGameTotalLaunches(version.getGame());

        model.addAttribute("version", version);
        model.addAttribute("game", version.getGame());
        model.addAttribute("entryPoint", entryPoint);

        return "running_game";
    }
}