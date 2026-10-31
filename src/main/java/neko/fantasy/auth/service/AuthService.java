package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.EmailVerificationPurpose;
import neko.fantasy.auth.domain.RefreshToken;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.oauth.OAuthRefreshTokenRepository;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.security.JwtService;
import neko.fantasy.auth.settings.SettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration STALE_SESSION_RETENTION = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthRefreshTokenRepository oauthRefreshTokenRepository;
    private final UserPasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final LoginRateLimiter rateLimiter;
    private final SettingsService settingsService;
    private final EmailVerificationService emailVerificationService;
    private final ObjectMapper objectMapper;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       OAuthRefreshTokenRepository oauthRefreshTokenRepository,
                       UserPasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       JwtService jwtService, AuthProperties properties,
                       LoginRateLimiter rateLimiter, SettingsService settingsService,
                       EmailVerificationService emailVerificationService,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oauthRefreshTokenRepository = oauthRefreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.settingsService = settingsService;
        this.emailVerificationService = emailVerificationService;
        this.objectMapper = objectMapper;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-42");
    }

    @Transactional
    public AuthResult register(AuthRequests.Register request, HttpServletRequest servletRequest) {
        User user = registerAccount(request);
        return createSession(user, servletRequest, user.getLastLoginAt(), request.device());
    }

    /**
     * 创建账号但**不签发 NekoAuth 浏览器会话**，供可信服务端桥接使用。
     * 密码与邮箱验证码仍由本服务统一校验，调用方只负责传递原始注册信息。
     */
    @Transactional
    public User registerAccount(AuthRequests.Register request) {
        return createAccount(request.username(), request.email(), request.password(),
                request.verificationCode(), true, true);
    }

    /**
     * 管理后台创建普通用户：仍由 NekoAuth 保存密码与身份，但跳过邮箱验证码。
     * 管理员权限已在调用方后台校验。
     */
    @Transactional
    public User createAccountForAdmin(String rawEmail, String password) {
        return createAccount(null, rawEmail, password, null, false, false);
    }

    private User createAccount(String rawUsername, String rawEmail, String password,
                               String verificationCode, boolean requireVerificationCode,
                               boolean enforceRegistrationPolicy) {
        passwordPolicy.validate(password);
        String requestedUsername = trimToNull(rawUsername);
        String username = requestedUsername == null
                ? generateUsername()
                : requestedUsername.toLowerCase(Locale.ROOT);
        String email = normalizeEmail(rawEmail);
        if (enforceRegistrationPolicy) {
            settingsService.requireRegistrationAllowed(email);
        }

        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "该用户名已被使用");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "该邮箱已被注册");
        }

        boolean emailVerified = false;
        if (requireVerificationCode && emailVerificationService.isRequired()) {
            emailVerificationService.verifyAndConsume(
                    email, verificationCode, EmailVerificationPurpose.REGISTRATION);
            emailVerified = true;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(defaultDisplayName(email));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmailVerified(emailVerified);
        user.setLastLoginAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public AuthResult login(AuthRequests.Login request, HttpServletRequest servletRequest) {
        String clientKey = clientKey(servletRequest);
        rateLimiter.check(clientKey);

        User user;
        try {
            user = authenticateAccount(request.identifier(), request.password());
        } catch (ApiException exception) {
            rateLimiter.recordFailure(clientKey);
            throw exception;
        }

        rateLimiter.recordSuccess(clientKey);
        return createSession(user, servletRequest, user.getLastLoginAt(), request.device());
    }

    /**
     * 校验账号口令并刷新最后登录时间，但不创建任何会话。
     * 供内部账号桥接使用；登录限流由桥接调用方在其用户入口完成。
     */
    @Transactional
    public User authenticateAccount(String rawIdentifier, String password) {
        String identifier = rawIdentifier.trim();
        User user = identifier.contains("@")
                ? userRepository.findByEmail(normalizeEmail(identifier)).orElse(null)
                : userRepository.findByUsername(identifier.toLowerCase(Locale.ROOT)).orElse(null);

        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user == null || !passwordMatches) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "该账户已被停用");
        }

        if (passwordEncoder.needsUpgrade(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        user.setLastLoginAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, HttpServletRequest servletRequest) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("REFRESH_REQUIRED", "登录状态已失效，请重新登录");
        }

        Instant now = Instant.now();
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hashToken(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "登录状态已失效，请重新登录"));

        if (stored.getRevokedAt() != null) {
            String familyId = stored.getFamilyId() == null ? stored.getTokenHash() : stored.getFamilyId();
            refreshTokenRepository.revokeFamily(familyId, now);
            throw ApiException.unauthorized("REFRESH_REUSED", "检测到异常登录会话，请重新登录");
        }
        if (!stored.getExpiresAt().isAfter(now) || !stored.getUser().isEnabled()) {
            stored.setRevokedAt(now);
            throw ApiException.unauthorized("REFRESH_EXPIRED", "登录状态已过期，请重新登录");
        }

        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);

        User user = stored.getUser();
        Instant authenticatedAt = stored.getAuthTime() == null
                ? (user.getLastLoginAt() == null ? now : user.getLastLoginAt())
                : stored.getAuthTime();
        String newRawToken = randomToken();
        refreshTokenRepository.save(new RefreshToken(
                user,
                hashToken(newRawToken),
                authenticatedAt,
                now.plus(properties.jwt().refreshTtl()),
                clientIp(servletRequest),
                truncate(servletRequest.getHeader("User-Agent"), 512),
                stored.getDeviceInfo(),
                stored.getFamilyId() == null ? stored.getTokenHash() : stored.getFamilyId()
        ));

        return authResult(user, newRawToken, authenticatedAt);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public ApiResponses.User me(Long userId) {
        return ApiResponses.User.from(requireUser(userId));
    }

    @Transactional
    public void changePassword(Long userId, AuthRequests.ChangePassword request) {
        passwordPolicy.validate(request.newPassword());
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        replacePassword(user, request.newPassword());
    }

    @Transactional
    public void changePasswordByEmail(String rawEmail, AuthRequests.ChangePassword request) {
        User user = userRepository.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));
        changePassword(user.getId(), request);
    }

    /**
     * 管理员重置普通用户密码。管理员权限由调用它的管理后台校验；这里只负责统一改密与撤销会话。
     */
    @Transactional
    public void setPasswordByEmail(String rawEmail, String newPassword) {
        passwordPolicy.validate(newPassword);
        User user = userRepository.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "用户不存在"));
        replacePassword(user, newPassword);
    }

    private void replacePassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);
        revokeSessionsAndOAuthGrants(user.getId());
    }

    public EmailVerificationService.CodeDelivery sendPasswordResetCode(String email) {
        return emailVerificationService.sendPasswordResetCode(email);
    }

    @Transactional
    public void resetPassword(AuthRequests.ResetPassword request) {
        passwordPolicy.validate(request.newPassword());
        String email = normalizeEmail(request.email());
        emailVerificationService.verifyAndConsume(
                email, request.code(), EmailVerificationPurpose.PASSWORD_RESET);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest(
                        "PASSWORD_RESET_INVALID", "邮箱或验证码不正确"));
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);
        revokeSessionsAndOAuthGrants(user.getId());
    }

    @Transactional(readOnly = true)
    public List<ApiResponses.Session> sessions(Long userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken == null || currentRefreshToken.isBlank()
                ? null : hashToken(currentRefreshToken);
        return refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream()
                .map(token -> new ApiResponses.Session(
                        token.getId(), token.getIpAddress(), token.getUserAgent(),
                        readDeviceInfo(token.getDeviceInfo()), token.getCreatedAt(),
                        token.getLastUsedAt(), token.getExpiresAt(), token.getTokenHash().equals(currentHash)))
                .toList();
    }

    /**
     * 读取浏览器当前有效的登录会话。供 OIDC 登出关联 {@code id_token_hint} 与
     * 当前浏览器用户使用；不刷新会话，也不延长有效期。
     */
    @Transactional(readOnly = true)
    public Optional<ActiveSession> findActiveSession(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .filter(token -> token.isActive(now))
                .filter(token -> token.getUser().isEnabled())
                .map(token -> new ActiveSession(
                        token.getUser().getId(), token.getAuthTime(), token.getId()));
    }

    @Transactional
    public boolean revokeSession(Long userId, Long sessionId, String currentRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "会话不存在"));
        if (token.getRevokedAt() != null) {
            return false;
        }
        token.setRevokedAt(Instant.now());
        String currentHash = currentRefreshToken == null || currentRefreshToken.isBlank()
                ? null : hashToken(currentRefreshToken);
        return token.getTokenHash().equals(currentHash);
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private void revokeSessionsAndOAuthGrants(Long userId) {
        Instant now = Instant.now();
        refreshTokenRepository.revokeAllForUser(userId, now);
        oauthRefreshTokenRepository.revokeAllForUser(userId, now);
    }

    @Scheduled(cron = "0 23 4 * * *")
    @Transactional
    public void deleteStaleSessions() {
        refreshTokenRepository.deleteStale(Instant.now().minus(STALE_SESSION_RETENTION));
    }

    private AuthResult createSession(User user, HttpServletRequest request, Instant authenticatedAt,
                                     AuthRequests.DeviceProfile device) {
        String rawToken = randomToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                hashToken(rawToken),
                authenticatedAt,
                Instant.now().plus(properties.jwt().refreshTtl()),
                clientIp(request),
                truncate(request.getHeader("User-Agent"), 512),
                writeDeviceInfo(device),
                UUID.randomUUID().toString()
        );
        refreshTokenRepository.save(refreshToken);
        return authResult(user, rawToken, authenticatedAt);
    }

    private AuthResult authResult(User user, String rawRefreshToken, Instant authenticatedAt) {
        JwtService.IssuedToken accessToken = jwtService.issue(user, authenticatedAt);
        long expiresIn = Math.max(0, Duration.between(Instant.now(), accessToken.expiresAt()).toSeconds());
        ApiResponses.Auth auth = new ApiResponses.Auth(
                accessToken.value(), "Bearer", expiresIn, ApiResponses.User.from(user));
        return new AuthResult(auth, rawRefreshToken);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "用户不存在"));
    }

    private String writeDeviceInfo(AuthRequests.DeviceProfile device) {
        if (device == null) {
            return null;
        }
        ApiResponses.DeviceInfo sanitized = new ApiResponses.DeviceInfo(
                deviceType(device.type()),
                clean(device.platform(), 40),
                clean(device.platformVersion(), 40),
                clean(device.architecture(), 24),
                clean(device.model(), 80),
                clean(device.browser(), 40),
                clean(device.browserVersion(), 40),
                clean(device.screen(), 32),
                clean(device.timezone(), 64),
                device.touch(),
                clean(device.source(), 24)
        );
        if (sanitized.type() == null && sanitized.platform() == null
                && sanitized.browser() == null && sanitized.model() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private ApiResponses.DeviceInfo readDeviceInfo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, ApiResponses.DeviceInfo.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static String deviceType(String value) {
        String type = clean(value, 16);
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "desktop", "mobile", "tablet" -> type.toLowerCase(Locale.ROOT);
            default -> "unknown";
        };
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String defaultDisplayName(String email) {
        int separator = email.indexOf('@');
        String localPart = (separator > 0 ? email.substring(0, separator) : email).strip();
        if (localPart.isEmpty()) {
            localPart = "Neko";
        }
        if (localPart.codePointCount(0, localPart.length()) <= 20) {
            return localPart;
        }
        return localPart.substring(0, localPart.offsetByCodePoints(0, 20));
    }

    private String generateUsername() {
        for (int attempt = 0; attempt < 16; attempt++) {
            byte[] random = new byte[8];
            SECURE_RANDOM.nextBytes(random);
            String candidate = "neko_" + HexFormat.of().formatHex(random);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique username");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String clientKey(HttpServletRequest request) {
        return clientIp(request);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AuthResult(ApiResponses.Auth auth, String refreshToken) {
    }

    public record ActiveSession(Long userId, Instant authenticatedAt, Long sessionId) {
    }
}
