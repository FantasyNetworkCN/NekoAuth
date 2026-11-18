package neko.fantasy.auth.service;

import jakarta.persistence.EntityManager;
import neko.fantasy.auth.domain.SmsRegistrationChallenge;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.SmsRegistrationChallengeRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.settings.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SmsRegistrationService {

    static final Duration TTL = Duration.ofSeconds(360);
    static final Duration COOLDOWN = Duration.ofMinutes(1);
    private static final Duration SEND_WINDOW = Duration.ofDays(1);
    private static final int MAX_SENDS_PER_PHONE = 5;
    private static final int MAX_ATTEMPTS = 5;
    public static final String PHONE_PATTERN = "^(?:\\+86)?1[3-9][0-9]{9}$";
    private static final Pattern CHINESE_MOBILE = Pattern.compile(PHONE_PATTERN);

    private final SettingsService settingsService;
    private final UserRepository users;
    private final SmsRegistrationChallengeRepository challenges;
    private final EntityManager entityManager;
    private final DypnsSmsClient smsClient;

    private final Map<String, Window> ipWindows = new HashMap<>();
    private Window globalWindow;

    public SmsRegistrationService(SettingsService settingsService,
                                  UserRepository users, SmsRegistrationChallengeRepository challenges,
                                  EntityManager entityManager, DypnsSmsClient smsClient) {
        this.settingsService = settingsService;
        this.users = users;
        this.challenges = challenges;
        this.entityManager = entityManager;
        this.smsClient = smsClient;
    }

    public void requireEnabled() {
        settingsService.requirePhoneRegistrationEnabled();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ApiResponses.VerificationCodeSent send(String rawPhone, String clientIp) {
        var config = settingsService.getSmsConfig();
        String phone = normalizePhone(rawPhone);
        requireAvailable(phone);
        return send(phone, clientIp, config, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ApiResponses.VerificationCodeSent sendBindingCode(String phone, String clientIp, Long userId) {
        return send(phone, clientIp, settingsService.getSmsBindingConfig(), userId);
    }

    private ApiResponses.VerificationCodeSent send(String phone, String clientIp, SettingsService.SmsConfig config,
                                                   Long bindingUserId) {
        Instant now = Instant.now();
        var stored = challenges.findForUpdate(phone);
        var challenge = stored.orElseGet(() -> new SmsRegistrationChallenge(phone, now));
        if (challenge.coolingDown(now, COOLDOWN)) {
            throw ApiException.tooManyRequests("SMS_CODE_COOLDOWN", "请等 60 秒后再发送验证码");
        }
        challenge.resetSendWindowIfExpired(now, SEND_WINDOW);
        if (challenge.getSendCount() >= MAX_SENDS_PER_PHONE) {
            throw ApiException.tooManyRequests("SMS_CODE_DAILY_LIMIT", "该手机号 24 小时内最多发送 5 次验证码");
        }
        reserveIpAndServiceQuota(clientIp, now);
        challenge.reserveSend(now);
        if (stored.isEmpty()) {
            // Concurrent first requests must collide on INSERT, not merge over a fresh reservation.
            entityManager.persist(challenge);
        }
        challenges.flush();
        String outId = UUID.randomUUID().toString();
        smsClient.send(config, phone, outId);
        challenge.activate(outId, now.plus(TTL), bindingUserId);
        return new ApiResponses.VerificationCodeSent("验证码已发送，请查收短信", TTL.toSeconds(), COOLDOWN.toSeconds());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyAndConsume(String phone, String code) {
        verifyAndConsume(phone, code, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyBindingCode(String phone, String code, Long userId) {
        verifyAndConsume(phone, code, userId);
    }

    private void verifyAndConsume(String phone, String code, Long bindingUserId) {
        var challenge = challenges.findForUpdate(phone).orElseThrow(SmsRegistrationService::invalidCode);
        if (challenge.getOutId() == null || !Objects.equals(challenge.getBindingUserId(), bindingUserId)) {
            throw invalidCode();
        }
        requireUnexpired(challenge);
        var config = bindingUserId == null ? settingsService.getSmsConfig() : settingsService.getSmsBindingConfig();
        boolean verified = smsClient.verify(config, phone, challenge.getOutId(), code);
        requireUnexpired(challenge);
        if (verified) {
            challenge.consume();
        } else if (challenge.recordFailedAttempt() >= MAX_ATTEMPTS) {
            challenge.consume();
            throw ApiException.badRequest("SMS_CODE_ATTEMPTS_EXHAUSTED", "短信验证码错误次数过多，请重新获取");
        } else {
            throw invalidCode();
        }
    }

    @Scheduled(cron = "0 22 4 * * *")
    @Transactional
    void deleteExpiredChallenges() {
        challenges.deleteOlderThan(Instant.now().minus(SEND_WINDOW));
    }

    public void requireAvailable(String phone) {
        if (users.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "该手机号已被注册");
        }
    }

    public static String normalizePhone(String phone) {
        if (!isPhone(phone)) {
            throw ApiException.badRequest("PHONE_INVALID", "请输入有效的中国大陆手机号");
        }
        return phone.startsWith("+86") ? phone : "+86" + phone;
    }

    static boolean isPhone(String value) {
        return CHINESE_MOBILE.matcher(value).matches();
    }

    private static ApiException invalidCode() {
        return ApiException.badRequest("SMS_CODE_INVALID", "短信验证码不正确或已过期");
    }

    private static void requireUnexpired(SmsRegistrationChallenge challenge) {
        if (!challenge.isActive(Instant.now())) {
            challenge.consume();
            throw ApiException.badRequest("SMS_CODE_EXPIRED", "短信验证码已过期，请重新获取");
        }
    }

    private synchronized void reserveIpAndServiceQuota(String ip, Instant now) {
        globalWindow = activeWindow(globalWindow, now, Duration.ofMinutes(1));
        Window clientWindow = activeWindow(ipWindows.get(ip), now, Duration.ofHours(1));
        if (globalWindow.count() >= 30 || clientWindow.count() >= 10) {
            throw ApiException.tooManyRequests("SMS_CODE_RATE_LIMIT", "短信发送过于频繁，请稍后再试");
        }
        globalWindow = globalWindow.increment();
        ipWindows.put(ip, clientWindow.increment());
    }

    @Scheduled(fixedDelay = 600_000)
    synchronized void removeExpiredIpWindows() {
        Instant now = Instant.now();
        ipWindows.values().removeIf(window -> !window.expiresAt().isAfter(now));
    }

    private static Window activeWindow(Window window, Instant now, Duration duration) {
        return window != null && window.expiresAt().isAfter(now) ? window : new Window(0, now.plus(duration));
    }

    private record Window(int count, Instant expiresAt) {
        Window increment() {
            return new Window(count + 1, expiresAt);
        }
    }
}
