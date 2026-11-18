package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.RegistrationCaptcha;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.RegistrationCaptchaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class RegistrationCaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int ANSWER_LENGTH = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String[] COLORS = {"#ff9edb", "#ffd28a", "#8ff2dd", "#c7a7ff", "#ffb3ae"};

    private final RegistrationCaptchaRepository repository;
    private final SecretKeySpec key;
    private final Duration ttl;

    public RegistrationCaptchaService(RegistrationCaptchaRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.ttl = properties.registration().captchaTtl();
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256").digest(
                    ("nekoauth-registration-captcha:" + properties.jwt().secret())
                            .getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(secret, HMAC_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化人机验证密钥", exception);
        }
    }

    @Transactional
    public IssuedCaptcha issue() {
        String id = UUID.randomUUID().toString();
        String answer = generateAnswer();
        RegistrationCaptcha captcha = new RegistrationCaptcha();
        captcha.setId(id);
        captcha.setAnswerHash(hash(id, answer));
        captcha.setExpiresAt(Instant.now().plus(ttl));
        captcha.setAttempts(0);
        repository.saveAndFlush(captcha);
        return new IssuedCaptcha(id, answer, imageDataUri(answer), ttl.toSeconds());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyAndConsume(String id, String rawAnswer) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(rawAnswer)) {
            throw ApiException.badRequest("CAPTCHA_REQUIRED", "请先完成人机验证");
        }

        RegistrationCaptcha captcha = repository.findByIdForUpdate(id.trim())
                .orElseThrow(() -> ApiException.badRequest(
                        "CAPTCHA_INVALID", "人机验证码无效或已使用，请刷新后重试"));
        Instant now = Instant.now();
        if (!captcha.getExpiresAt().isAfter(now)) {
            repository.delete(captcha);
            throw ApiException.badRequest("CAPTCHA_EXPIRED", "人机验证码已过期，请刷新后重试");
        }

        String answer = rawAnswer.trim().toUpperCase(Locale.ROOT);
        String expected = captcha.getAnswerHash();
        String actual = hash(captcha.getId(), answer);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            int attempts = captcha.getAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                repository.delete(captcha);
                throw ApiException.badRequest(
                        "CAPTCHA_TOO_MANY_ATTEMPTS", "人机验证失败次数过多，请刷新后重试");
            }
            captcha.setAttempts(attempts);
            repository.saveAndFlush(captcha);
            throw ApiException.badRequest("CAPTCHA_INVALID", "人机验证码不正确，请重新输入");
        }

        repository.delete(captcha);
    }

    @Scheduled(cron = "0 11 4 * * *")
    @Transactional
    public void deleteExpiredCaptchas() {
        repository.deleteExpired(Instant.now());
    }

    private String hash(String id, String answer) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            mac.update(id.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(answer.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算人机验证摘要", exception);
        }
    }

    private static String generateAnswer() {
        StringBuilder answer = new StringBuilder(ANSWER_LENGTH);
        for (int index = 0; index < ANSWER_LENGTH; index++) {
            answer.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return answer.toString();
    }

    private static String imageDataUri(String answer) {
        var image = new BufferedImage(180, 56, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.decode("#181022"));
            graphics.fillRect(0, 0, 180, 56);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD | Font.ITALIC, 28));
            for (int index = 0; index < answer.length(); index++) {
                int x = 16 + index * 32 + RANDOM.nextInt(5);
                int y = 38 + RANDOM.nextInt(7) - 3;
                var transform = graphics.getTransform();
                graphics.rotate(Math.toRadians(RANDOM.nextInt(31) - 15), x, y);
                graphics.setColor(Color.decode(COLORS[index % COLORS.length]));
                graphics.drawString(answer.substring(index, index + 1), x, y);
                graphics.setTransform(transform);
            }
            for (int index = 0; index < 6; index++) {
                graphics.setColor(new Color(255, 190, 224, 100));
                graphics.draw(new QuadCurve2D.Double(
                        RANDOM.nextInt(45), RANDOM.nextInt(56), RANDOM.nextInt(180), RANDOM.nextInt(56),
                        135 + RANDOM.nextInt(45), RANDOM.nextInt(56)));
            }
            for (int index = 0; index < 40; index++) {
                graphics.setColor(new Color(185, 170, 255, 125));
                graphics.fillOval(RANDOM.nextInt(180), RANDOM.nextInt(56), 3, 3);
            }
        } finally {
            graphics.dispose();
        }

        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("无法绘制人机验证码", exception);
        }
    }

    public record IssuedCaptcha(String id, String answer, String image, long expiresInSeconds) {
    }
}
