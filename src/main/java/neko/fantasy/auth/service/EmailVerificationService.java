package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.EmailVerificationCode;
import neko.fantasy.auth.domain.EmailVerificationSendLimit;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.EmailVerificationCodeRepository;
import neko.fantasy.auth.repository.EmailVerificationSendLimitRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.MailService;
import neko.fantasy.auth.settings.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.concurrent.locks.ReentrantLock;

@Service
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int LOCK_STRIPES = 256;

    private final EmailVerificationCodeRepository codeRepository;
    private final EmailVerificationSendLimitRepository sendLimitRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final MailService mailService;
    private final TransactionTemplate transactionTemplate;
    private final SecretKeySpec codeKey;
    private final boolean required;
    private final Duration codeTtl;
    private final Duration resendCooldown;
    private final int dailyLimit;
    private final Duration dailyWindow;
    private final ReentrantLock[] sendLocks = new ReentrantLock[LOCK_STRIPES];

    public EmailVerificationService(
            EmailVerificationCodeRepository codeRepository,
            EmailVerificationSendLimitRepository sendLimitRepository,
            UserRepository userRepository,
            SettingsService settingsService,
            MailService mailService,
            AuthProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.codeRepository = codeRepository;
        this.sendLimitRepository = sendLimitRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.required = properties.registration().requireEmailVerification();
        this.codeTtl = properties.registration().emailCodeTtl();
        this.resendCooldown = properties.registration().emailCodeCooldown();
        this.dailyLimit = Math.max(1, properties.registration().emailCodeDailyLimit());
        this.dailyWindow = properties.registration().emailCodeWindow();
        for (int index = 0; index < sendLocks.length; index++) {
            sendLocks[index] = new ReentrantLock();
        }
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

    public CodeDelivery sendRegistrationCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        settingsService.requireRegistrationAllowed(email);
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "该邮箱已被注册");
        }

        SettingsService.SmtpConfig smtp = settingsService.getSmtpConfig();
        String code = generateCode();
        ReentrantLock lock = lockFor(email);
        lock.lock();
        try {
            Instant now = Instant.now();
            transactionTemplate.executeWithoutResult(status -> reserveSend(email, code, now));
        } finally {
            lock.unlock();
        }

        mailService.sendRegistrationCode(email, smtp, code, codeTtl);
        return new CodeDelivery(
                "验证码已发送，请查收邮件",
                codeTtl.toSeconds(),
                resendCooldown.toSeconds());
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
        Instant now = Instant.now();
        codeRepository.deleteExpired(now);
        sendLimitRepository.deleteOlderThan(now.minus(dailyWindow));
    }

    private void reserveSend(String email, String code, Instant now) {
        EmailVerificationSendLimit limit = sendLimitRepository.findByEmailForUpdate(email).orElse(null);
        if (limit == null) {
            limit = new EmailVerificationSendLimit();
            limit.setEmail(email);
            limit.setWindowStartedAt(now);
            limit.setSentCount(0);
        } else {
            Instant availableAt = limit.getLastSentAt().plus(resendCooldown);
            if (availableAt.isAfter(now)) {
                throw ApiException.tooManyRequests(
                        "EMAIL_CODE_COOLDOWN",
                        "验证码发送过于频繁，请在 " + remainingSeconds(now, availableAt) + " 秒后重试");
            }
            Instant windowEndsAt = limit.getWindowStartedAt().plus(dailyWindow);
            if (!windowEndsAt.isAfter(now)) {
                limit.setWindowStartedAt(now);
                limit.setSentCount(0);
            }
        }

        Instant windowEndsAt = limit.getWindowStartedAt().plus(dailyWindow);
        if (limit.getSentCount() >= dailyLimit) {
            long retryAfterHours = Math.max(1, (remainingSeconds(now, windowEndsAt) + 3599) / 3600);
            throw ApiException.tooManyRequests(
                    "EMAIL_CODE_DAILY_LIMIT",
                    "该邮箱 24 小时内最多发送 " + dailyLimit + " 次验证码，请在约 "
                            + retryAfterHours + " 小时后重试");
        }

        limit.setLastSentAt(now);
        limit.setSentCount(limit.getSentCount() + 1);
        sendLimitRepository.saveAndFlush(limit);

        EmailVerificationCode record = codeRepository.findByEmailForUpdate(email).orElse(null);
        if (record == null) {
            record = new EmailVerificationCode();
            record.setEmail(email);
        }
        record.setCodeHash(hashCode(email, code));
        record.setExpiresAt(now.plus(codeTtl));
        record.setLastSentAt(now);
        record.setAttempts(0);
        codeRepository.saveAndFlush(record);
    }

    private ReentrantLock lockFor(String email) {
        return sendLocks[Math.floorMod(email.hashCode(), sendLocks.length)];
    }

    private static long remainingSeconds(Instant now, Instant availableAt) {
        long remainingMillis = Math.max(1, Duration.between(now, availableAt).toMillis());
        return Math.max(1, (remainingMillis + 999) / 1000);
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
