package ru.vlsu.myng.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Set;

/**
 * Разделяет запросы между двумя origin приложения.
 *
 * <ul>
 *   <li>На порту игр ({@code app.games.port}) отдаются только файлы игр
 *       и скрипт-патч для localStorage. Страницы сайта и API там недоступны.</li>
 *   <li>На основном порту файлы игр ({@code /static/**}) не отдаются,
 *       чтобы JS игры никогда не выполнялся на origin сайта.</li>
 * </ul>
 *
 * <p>
 * Путь берётся в декодированном виде (через {@link UrlPathHelper}),
 * поэтому фильтр нельзя обойти записью вроде {@code /static/%67amefiles/...}.
 * </p>
 */
public class GameOriginFilter extends OncePerRequestFilter {

    static final String GAME_FILES_PREFIX = "/static/";

    private static final Set<String> GAME_ORIGIN_EXTRA_PATHS = Set.of(
            "/__myng-storage-patch.js",
            "/favicon.ico"
    );

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final int gamesPort;

    public GameOriginFilter(int gamesPort) {
        this.gamesPort = gamesPort;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = PATH_HELPER.getPathWithinApplication(request);
        boolean isGameFile = path.startsWith(GAME_FILES_PREFIX);
        boolean onGamesPort = request.getLocalPort() == gamesPort;

        boolean allowed = onGamesPort
                ? isGameFile || GAME_ORIGIN_EXTRA_PATHS.contains(path)
                : !isGameFile;

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
