package ru.vlsu.myng.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class ImageTypeTest {

    @Test
    void detect_RecognizesSupportedFormats() {
        assertEquals(MediaType.IMAGE_PNG,
                ImageType.detect(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }));
        assertEquals(MediaType.IMAGE_JPEG,
                ImageType.detect(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }));
        assertEquals(MediaType.IMAGE_GIF, ImageType.detect("GIF89a...".getBytes()));
        assertEquals("image/webp", ImageType.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes()).toString());
    }

    @Test
    void detect_RejectsEverythingElse() {
        assertNull(ImageType.detect(null));
        assertNull(ImageType.detect(new byte[0]));
        assertNull(ImageType.detect("<script>alert(1)</script>".getBytes()));
        // SVG — тоже «картинка», но может содержать скрипты, поэтому не принимается
        assertNull(ImageType.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes()));
    }

    @Test
    void readImage_IgnoresContentTypeSentByClient() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "cover.png", "image/png", "<html></html>".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ImageType.readImage(fake, "Нужна картинка"));

        assertEquals("Нужна картинка", ex.getMessage());
    }
}
