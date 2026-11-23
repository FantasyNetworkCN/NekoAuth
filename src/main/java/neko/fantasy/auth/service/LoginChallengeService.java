package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.domain.LoginChallenge;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.LoginChallengeRepository;
import neko.fantasy.auth.settings.SettingsService;
import neko.fantasy.auth.security.ClientIpResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LoginChallengeService {

    private final LoginChallengeRepository challenges;
    private final EmailVerificationService emailVerification;
    private final SmsRegistrationService smsVerification;
    private final SettingsService settings;
    private final ClientIpResolver clientIps;
    private final Duration challengeTtl;
    private final int maxAttempts;

    public LoginChallengeService(
            LoginChallengeRepository challenges,
            EmailVerificationService emailVerification,
            SmsRegistrationService smsVerification,
            SettingsService settings,
            ClientIpResolver clientIps,
            @Value("${auth.risk.challenge-ttl:10m}") Duration challengeTtl,
            @Value("${auth.risk.challenge-attempts:5}") int maxAttempts
    ) {
        this.challenges = challenges;
        this.emailVerification = emailVerification;
        this.smsVerification = smsVerification;
        this.settings = settings;
        this.clientIps = clientIps;
        this.challengeTtl = challengeTtl;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public CreatedChallenge create(User user, Instant authenticatedAt,
                                   LoginRiskService.RiskEvaluation risk, String deviceInfoJson,
                                   HttpServletRequest request) {
        List<String> methods = availableMethods(user);
        if (methods.isEmpty()) {
            throw ApiException.forbidden(
                    "LOGIN_VERIFICATION_UNAVAILABLE",
                    "当前登录环境需要额外验证，但账户没有可用的邮箱或短信验证方式");
        }
        String preferred = preferredMethod(user, risk.score(), methods);
        String challengeId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        LoginChallenge challenge = new LoginChallenge(
                challengeId, user, risk.score(), risk.level(), risk.reasonText(),
                preferred, target(user, preferred), deviceInfoJson, authenticatedAt,
                now, now.plus(challengeTtl));
        challenges.saveAndFlush(challenge);

        Delivery delivery = sendWithFallback(user, preferred, methods, challengeId, request);
        challenge.changeMethod(delivery.method(), delivery.target(), now, now.plus(challengeTtl));
        challenges.saveAndFlush(challenge);
        return new CreatedChallenge(view(challenge, methods, delivery.resendAfterSeconds(), now));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ApiResponses.LoginStepUp resend(String challengeId, String requestedMethod, HttpServletRequest request) {
        Instant now = Instant.now();
        LoginChallenge challenge = requireUsable(challengeId, now);
        User user = challenge.getUser();
        List<String> methods = availableMethods(user);
        String method = requestedMethod == null || requestedMethod.isBlank()
                ? challenge.getMethod() : requestedMethod.trim().toLowerCase(Locale.ROOT);
        if (requestedMethod == null || requestedMethod.isBlank()) {
            if (!methods.contains(method)) method = methods.getFirst();
        }
        if (!methods.contains(method)) {
            throw ApiException.badRequest("LOGIN_STEP_UP_METHOD_UNAVAILABLE", "当前账户无法使用该验证方式");
        }

        Delivery delivery;
        try {
            delivery = send(user, method, challenge.getId(), request);
        } catch (ApiException exception) {
            if (requestedMethod != null && !requestedMethod.isBlank()) throw exception;
            String fallback = other(method, methods);
            if (fallback == null) throw exception;
            delivery = send(user, fallback, challenge.getId(), request);
        }
        challenge.changeMethod(delivery.method(), delivery.target(), now, now.plus(challengeTtl));
        challenges.saveAndFlush(challenge);
        return view(challenge, methods, delivery.resendAfterSeconds(), now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public VerifiedLogin verifyAndConsume(String challengeId, String code) {
        Instant now = Instant.now();
        LoginChallenge challenge = requireUsable(challengeId, now);
        User user = challenge.getUser();
        if (!matchesCurrentTarget(user, challenge)) {
            challenge.consume(now);
            throw ApiException.badRequest("LOGIN_STEP_UP_INVALID", "登录验证已失效，请重新登录");
        }

        try {
            if ("email".equals(challenge.getMethod())) {
                emailVerification.verifyLoginStepUpCode(user.getEmail(), code, challenge.getId());
            } else if ("sms".equals(challenge.getMethod())) {
                smsVerification.verifyLoginStepUpCode(user.getPhone(), code, challenge.getId());
            } else {
                throw ApiException.badRequest("LOGIN_STEP_UP_INVALID", "登录验证方式无效，请重新登录");
            }
        } catch (ApiException exception) {
            int attempts = challenge.recordFailedAttempt();
            if (attempts >= maxAttempts || exception.getCode().endsWith("TOO_MANY_ATTEMPTS")
                    || exception.getCode().endsWith("ATTEMPTS_EXHAUSTED")) {
                challenge.consume(now);
                challenges.saveAndFlush(challenge);
                throw ApiException.badRequest(
                        "LOGIN_STEP_UP_EXHAUSTED", "验证码错误次数过多，请重新登录");
            }
            challenges.saveAndFlush(challenge);
            throw exception;
        }

        challenge.consume(now);
        challenges.saveAndFlush(challenge);
        return new VerifiedLogin(user.getId(), challenge.getAuthTime(), challenge.getDeviceInfo(),
                challenge.getRiskScore(), challenge.getRiskLevel(), challenge.getRiskReasons());
    }

    private LoginChallenge requireUsable(String challengeId, Instant now) {
        LoginChallenge challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(LoginChallengeService::expired);
        if (challenge.getConsumedAt() != null
                || challenge.getExpiresAt() == null || !challenge.getExpiresAt().isAfter(now)
                || !challenge.getUser().isEnabled()) {
            challenge.consume(now);
            challenges.saveAndFlush(challenge);
            throw expired();
        }
        return challenge;
    }

    private Delivery sendWithFallback(User user, String preferred, List<String> methods,
                                      String challengeId, HttpServletRequest request) {
        ApiException failure = null;
        for (String method : ordered(preferred, methods)) {
            try {
                return send(user, method, challengeId, request);
            } catch (ApiException exception) {
                failure = exception;
            }
        }
        throw failure == null
                ? ApiException.forbidden("LOGIN_VERIFICATION_UNAVAILABLE", "当前没有可用的登录验证方式")
                : failure;
    }

    private Delivery send(User user, String method, String challengeId, HttpServletRequest request) {
        if ("sms".equals(method)) {
            var delivery = smsVerification.sendLoginStepUpCode(
                    user.getPhone(), clientIps.resolve(request), challengeId);
            return new Delivery("sms", user.getPhone(), delivery.resendAfterSeconds());
        }
        var delivery = emailVerification.sendLoginStepUpCode(user.getEmail(), challengeId);
        return new Delivery("email", user.getEmail(), delivery.resendAfterSeconds());
    }

    private List<String> availableMethods(User user) {
        List<String> methods = new ArrayList<>(2);
        if (settings.isSmtpAvailable() && user.getEmail() != null && !user.getEmail().isBlank()) {
            methods.add("email");
        }
        if (settings.isSmsVerificationEnabled() && user.getPhone() != null && !user.getPhone().isBlank()) {
            methods.add("sms");
        }
        return List.copyOf(methods);
    }

    private static String preferredMethod(User user, int score, List<String> methods) {
        if (score >= 70 && methods.contains("sms")) return "sms";
        if (methods.contains("email")) return "email";
        return methods.getFirst();
    }

    private static List<String> ordered(String preferred, List<String> methods) {
        List<String> ordered = new ArrayList<>(methods.size());
        ordered.add(preferred);
        for (String method : methods) {
            if (!method.equals(preferred)) ordered.add(method);
        }
        return ordered;
    }

    private static String other(String current, List<String> methods) {
        for (String method : methods) {
            if (!method.equals(current)) return method;
        }
        return null;
    }

    private static String target(User user, String method) {
        return "sms".equals(method) ? user.getPhone() : user.getEmail();
    }

    private static boolean matchesCurrentTarget(User user, LoginChallenge challenge) {
        String current = "sms".equals(challenge.getMethod()) ? user.getPhone() : user.getEmail();
        return current != null && current.equals(challenge.getTarget());
    }

    private static ApiResponses.LoginStepUp view(LoginChallenge challenge, List<String> methods,
                                                 long resendAfterSeconds, Instant now) {
        return new ApiResponses.LoginStepUp(
                "STEP_UP_REQUIRED",
                challenge.getId(),
                methods,
                challenge.getMethod(),
                maskTarget(challenge.getMethod(), challenge.getTarget()),
                Math.max(1, Duration.between(now, challenge.getExpiresAt()).toSeconds()),
                Math.max(1, resendAfterSeconds));
    }

    static String maskTarget(String method, String target) {
        if (target == null || target.isBlank()) return "";
        if ("sms".equals(method)) {
            String digits = target.replace("+86", "");
            if (digits.length() < 7) return "***";
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
        }
        int at = target.indexOf('@');
        if (at <= 0) return "***";
        String name = target.substring(0, at);
        String domain = target.substring(at);
        return name.substring(0, 1) + "***" + domain;
    }

    private static ApiException expired() {
        return ApiException.badRequest("LOGIN_STEP_UP_EXPIRED", "登录验证已过期，请重新登录");
    }

    @Scheduled(cron = "0 37 4 * * *")
    @Transactional
    void deleteExpiredChallenges() {
        challenges.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }

    private record Delivery(String method, String target, long resendAfterSeconds) {
    }

    public record CreatedChallenge(ApiResponses.LoginStepUp view) {
    }

    public record VerifiedLogin(Long userId, Instant authenticatedAt, String deviceInfoJson,
                                int riskScore, String riskLevel, String riskReasons) {
    }
}
