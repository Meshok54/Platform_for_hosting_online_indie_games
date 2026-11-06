package ru.vlsu.myng.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Отдельный origin для файлов игр.
 *
 * <p>
 * Игры — это чужой HTML и JavaScript, загруженный разработчиками.
 * Если отдавать их с того же origin, что и сайт, JS игры получает
 * полный доступ к сайту от имени игрока: может читать страницы,
 * отправлять запросы с его сессией, обращаться к родительской странице.
 * </p>
 *
 * <p>
 * Поэтому приложение слушает второй порт ({@code app.games.port}),
 * с которого отдаются только файлы игр. Для браузера это другой origin,
 * и правило same-origin policy изолирует игру от сайта.
 * В продакшене вместо порта использовался бы отдельный домен.
 * </p>
 */
@Configuration
public class GameServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> gameFilesConnector(
            @Value("${app.games.port}") int gamesPort) {

        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(gamesPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    public FilterRegistrationBean<GameOriginFilter> gameOriginFilter(
            @Value("${app.games.port}") int gamesPort) {

        FilterRegistrationBean<GameOriginFilter> registration =
                new FilterRegistrationBean<>(new GameOriginFilter(gamesPort));

        // раньше Spring Security: лишние запросы отсекаются сразу
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}
