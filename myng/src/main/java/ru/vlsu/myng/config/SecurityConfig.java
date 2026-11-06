package ru.vlsu.myng.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.vlsu.myng.services.CustomUserDetailsService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация безопасности приложения.
 * Содержит настройки шифрования паролей и другие компоненты безопасности.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final CustomUserDetailsService userDetailsService;
        private final BanFilter banFilter;

        /**
         * Создает бин для шифрования паролей.
         *
         * @return PasswordEncoder для шифрования паролей
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        /**
         * Правила для порта с файлами игр ({@code app.games.port}).
         *
         * <p>
         * Здесь отдаются только статические файлы (что именно — решает
         * {@link GameOriginFilter}), поэтому вход, сессии и CSRF не нужны.
         * Cookie сайта на этот origin не приходят.
         * </p>
         *
         * <p>
         * Встраивать игру в iframe разрешено только страницам сайта
         * (CSP {@code frame-ancestors}). Кэширование файлов игр не запрещается,
         * чтобы повторный запуск не скачивал всё заново.
         * </p>
         */
        @Bean
        @Order(1)
        public SecurityFilterChain gameFilesFilterChain(
                        HttpSecurity http,
                        @Value("${app.games.port}") int gamesPort,
                        @Value("${app.site.origins}") String siteOrigins) throws Exception {
                http
                                .securityMatcher(request -> request.getLocalPort() == gamesPort)
                                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .requestCache(cache -> cache.disable())
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.disable())
                                                .contentSecurityPolicy(csp -> csp
                                                                .policyDirectives("frame-ancestors " + siteOrigins))
                                                .cacheControl(cache -> cache.disable()));

                return http.build();
        }

        /**
         * Правила для основного сайта.
         */
        @Bean
        @Order(2)
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        // Ключ подписи cookie «Запомнить меня». Берётся из credentials.properties;
                        // если не задан, генерируется при каждом запуске (тогда после перезапуска
                        // сервера «запомненные» входы сбрасываются)
                        @Value("${app.remember-me.key:#{T(java.util.UUID).randomUUID().toString()}}")
                        String rememberMeKey) throws Exception {
                http
                                .headers(headers -> headers.frameOptions(f -> f.sameOrigin()))
                                // CSRF-защита включена (настройки по умолчанию): формы с th:action
                                // получают токен автоматически, fetch-запросы — через /js/csrf.js
                                .addFilterBefore(banFilter, UsernamePasswordAuthenticationFilter.class)
                                // Правила проверяются сверху вниз, срабатывает первое совпавшее,
                                // поэтому более узкие правила должны идти раньше широких
                                .authorizeHttpRequests(authz -> authz
                                                // Статика, файлы игр и служебные пути
                                                .requestMatchers(
                                                                "/main.css",
                                                                "/css/**",
                                                                "/webfonts/**",
                                                                "/js/**",
                                                                "/images/**",
                                                                "/favicon.ico",
                                                                "/__myng-storage-patch.js",
                                                                "/.well-known/**",
                                                                "/error")
                                                .permitAll()

                                                // Публичные страницы
                                                .requestMatchers(
                                                                "/",
                                                                "/index",
                                                                "/auth",
                                                                "/auth/**",
                                                                "/banned",
                                                                "/catalog",
                                                                "/catalog/tags/**",
                                                                "/running-game/**")
                                                .permitAll()

                                                // Страница игры и подгрузка отзывов доступны всем только на чтение
                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/games/{id:\\d+}",
                                                                "/reviews/more/**")
                                                .permitAll()

                                                // Публикация и управление играми
                                                .requestMatchers(
                                                                "/games/publish",
                                                                "/games/publish/**",
                                                                "/games/*/edit",
                                                                "/games/*/versions/**",
                                                                "/games/repo-tree",
                                                                "/games/repo-tree-json")
                                                .hasAnyRole("DEV", "MOD", "ADMIN")

                                                // Модерация
                                                .requestMatchers(
                                                                "/to-moderate/**",
                                                                "/mod-item/**")
                                                .hasAnyRole("MOD", "ADMIN")

                                                // Администрирование
                                                .requestMatchers(
                                                                "/user-list/**",
                                                                "/moderation-log/**")
                                                .hasRole("ADMIN")

                                                // Всё остальное (профиль, коллекции, отзывы, уведомления,
                                                // заявки на разработчика) — только для авторизованных
                                                .anyRequest().authenticated())

                                .formLogin(form -> form
                                                .loginPage("/auth")
                                                .loginProcessingUrl("/auth/login")
                                                .defaultSuccessUrl("/")
                                                .failureUrl("/auth?error=true")
                                                .usernameParameter("username")
                                                .passwordParameter("password")
                                                .permitAll())

                                .logout(logout -> logout
                                                .logoutUrl("/profile/logout")
                                                .logoutSuccessUrl("/")
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID")
                                                .permitAll())

                                .rememberMe(remember -> remember
                                                .key(rememberMeKey)
                                                .tokenValiditySeconds(86400)
                                                .rememberMeParameter("remember-me"))

                                .userDetailsService(userDetailsService);

                return http.build();
        }
}