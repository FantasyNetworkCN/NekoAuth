package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.EmailVerificationCode;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.EmailVerificationCodeRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final EmailVerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final MailService mailService;
    private final SecretKeySpec codeKey;
    private final boolean required;

    public EmailVerificationService(
            EmailVerificationCodeRepository codeRepository,
            UserRepository userRepository,
            SettingsService settingsService,
            MailService mailService,
            AuthProperties properties,
            @Value("${auth.registration.require-email-verification:true}") boolean required
    ) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.required = required;
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(
                    ("nekoauth-email-verification:" + properties.jwt().secret())
                            .getBytes(StandardCharsets.UTF_8));
            this.codeKey = new SecretKeySpec(key, HMAC_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化邮箱验证码密钥", exception);
        }
    }

    public boolean isRequired() {
        return required;
    }

    @Transactional
    public CodeDelivery sendRegistrationCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        settingsService.requireRegistrationAllowed(email);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "该邮箱已被注册");
        }

        SettingsService.SmtpConfig smtp = settingsService.getSmtpConfig();
        Instant now = Instant.now();
        EmailVerificationCode record = codeRepository.findByEmailForUpdate(email).orElse(null);
        if (record != null) {
            Instant availableAt = record.getLastSentAt().plus(RESEND_COOLDOWN);
            if (availableAt.isAfter(now)) {
                long remaining = Math.max(1, Duration.between(now, availableAt).toSeconds());
                throw ApiException.tooManyRequests(
                        "EMAIL_CODE_COOLDOWN", "验证码发送过于频繁，请在 " + remaining + " 秒后重试");
            }
        } else {
            record = new EmailVerificationCode();
            record.setEmail(email);
        }

        String code = generateCode();
        record.setCodeHash(hashCode(email, code));
        record.setExpiresAt(now.plus(CODE_TTL));
        record.setLastSentAt(now);
        record.setAttempts(0);
        codeRepository.saveAndFlush(record);
        mailService.sendRegistrationCode(email, smtp, code, CODE_TTL);
        return new CodeDelivery(
                "验证码已发送，请查收邮件",
                CODE_TTL.toSeconds(),
                RESEND_COOLDOWN.toSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyAndConsume(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        if (!StringUtils.hasText(code)) {
            throw ApiException.badRequest("EMAIL_VERIFICATION_REQUIRED", "请先获取邮箱验证码");
        }

        EmailVerificationCode record = codeRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> ApiException.badRequest(
                        "EMAIL_VERIFICATION_INVALID", "邮箱验证码无效或已过期，请重新获取"));
        Instant now = Instant.now();
        if (!record.getExpiresAt().isAfter(now)) {
            codeRepository.delete(record);
            throw ApiException.badRequest(
                    "EMAIL_VERIFICATION_EXPIRED", "邮箱验证码已过期，请重新获取");
        }

        String expected = record.getCodeHash();
        String actual = hashCode(email, code.trim());
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII))) {
            int attempts = record.getAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                codeRepository.delete(record);
                throw ApiException.badRequest(
                        "EMAIL_VERIFICATION_TOO_MANY_ATTEMPTS", "验证码错误次数过多，请重新获取");
            }
            record.setAttempts(attempts);
            codeRepository.saveAndFlush(record);
            throw ApiException.badRequest(
                    "EMAIL_VERIFICATION_INVALID",
                    "邮箱验证码不正确，还可尝试 " + (MAX_ATTEMPTS - attempts) + " 次");
        }

        codeRepository.delete(record);
    }

    @Scheduled(cron = "0 17 4 * * *")
    @Transactional
    public void deleteExpiredCodes() {
        codeRepository.deleteExpired(Instant.now());
    }

    private String hashCode(String email, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(codeKey);
            mac.update(email.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算邮箱验证码摘要", exception);
        }
    }

    private static String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public record CodeDelivery(String message, long expiresInSeconds, long resendAfterSeconds) {
    }
}
