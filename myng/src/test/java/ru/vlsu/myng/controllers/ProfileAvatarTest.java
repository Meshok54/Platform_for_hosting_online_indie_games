package ru.vlsu.myng.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.vlsu.myng.entities.User;
import ru.vlsu.myng.services.UserService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет, что аватар нельзя использовать для XSS.
 *
 * <p>
 * Раньше метод возвращал {@code byte[]} без типа, и Spring брал Content-Type
 * из заголовка Accept: файл, открытый по прямой ссылке в браузере,
 * отдавался как {@code text/html} и выполнялся на origin сайта.
 * </p>
 */
class ProfileAvatarTest {

    /** Заголовок Accept, который браузер отправляет при переходе по ссылке. */
    private static final String BROWSER_NAVIGATION_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProfileController(userService, null, null, null))
                .build();
    }

    private void givenAvatar(byte[] bytes) {
        User user = new User();
        user.setProfilePic(bytes);
        when(userService.findById(1)).thenReturn(user);
    }

    @Test
    void avatar_IsServedAsImage_EvenWhenBrowserAsksForHtml() throws Exception {
        givenAvatar(PNG);

        mockMvc.perform(get("/profile/1/avatar").header("Accept", BROWSER_NAVIGATION_ACCEPT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void avatar_ThatIsNotAnImage_IsNotServed() throws Exception {
        // такой файл мог остаться в базе со времён, когда тип проверялся только по заголовку
        givenAvatar("<script>alert(document.domain)</script>".getBytes());

        mockMvc.perform(get("/profile/1/avatar").header("Accept", BROWSER_NAVIGATION_ACCEPT))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyAvatar_ReturnsNotFound() throws Exception {
        givenAvatar(new byte[0]);

        mockMvc.perform(get("/profile/1/avatar"))
                .andExpect(status().isNotFound());
    }
}
