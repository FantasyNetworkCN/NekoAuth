package neko.fantasy.auth.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarImageValidatorTest {

    @Test
    void acceptsCompleteImagesWithoutPixelLimit() throws Exception {
        Map<AvatarImageValidator.Format, byte[]> images = Map.of(
                AvatarImageValidator.Format.PNG, image("png"),
                AvatarImageValidator.Format.JPEG, image("jpg"),
                AvatarImageValidator.Format.GIF, image("gif"),
                AvatarImageValidator.Format.BMP, image("bmp"),
                AvatarImageValidator.Format.WEBP, Base64.getDecoder().decode(
                        "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoBAAEAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA="));

        images.forEach((format, bytes) ->
                assertThat(AvatarImageValidator.validate(bytes)).isEqualTo(format));
    }

    @Test
    void rejectsTruncatedOrTrailingPayloads() throws Exception {
        Map<AvatarImageValidator.Format, byte[]> images = Map.of(
                AvatarImageValidator.Format.PNG, image("png"),
                AvatarImageValidator.Format.JPEG, image("jpg"),
                AvatarImageValidator.Format.GIF, image("gif"),
                AvatarImageValidator.Format.BMP, image("bmp"),
                AvatarImageValidator.Format.WEBP, Base64.getDecoder().decode(
                        "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoBAAEAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA="));

        for (byte[] bytes : images.values()) {
            assertThat(AvatarImageValidator.validate(Arrays.copyOf(bytes, bytes.length - 1))).isNull();
            assertThat(AvatarImageValidator.validate(append(bytes, "<script>alert(1)</script>")))
                    .isNull();
        }
    }

    @Test
    void rejectsFakeSignaturesAndSvg() {
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(AvatarImageValidator.validate(fakePng)).isNull();
        assertThat(AvatarImageValidator.validate(svg)).isNull();
        assertThat(AvatarImageValidator.validate(append(fakePng, "<html>not an image</html>")))
                .isNull();
    }

    private static byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static byte[] append(byte[] source, String suffix) {
        byte[] tail = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(source, source.length + tail.length);
        System.arraycopy(tail, 0, result, source.length, tail.length);
        return result;
    }
}
