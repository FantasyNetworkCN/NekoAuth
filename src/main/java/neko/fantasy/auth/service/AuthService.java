package neko.fantasy.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.RefreshToken;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import neko.fantasy.auth.security.JwtService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration STALE_SESSION_RETENTION = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final LoginRateLimiter rateLimiter;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                       JwtService jwtService, AuthProperties properties,
                       LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-42");
    }

    @Transactional
    public AuthResult register(AuthRequests.Register request, HttpServletRequest servletRequest) {
        passwordPolicy.validate(request.password());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = normalizeEmail(request.email());

        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "该用户名已被使用");
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "该邮箱已被注册");
        }

        String requestedDisplayName = request.displayName() == null ? "" : request.displayName().trim();
        String displayName = requestedDisplayName.isEmpty() ? request.username().trim() : requestedDisplayName;

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        return createSession(user, servletRequest);
    }

    @Transactional
    public AuthResult login(AuthRequests.Login request, HttpServletRequest servletRequest) {
        String clientKey = clientKey(servletRequest);
        rateLimiter.check(clientKey);

        String identifier = request.identifier().trim();
        User user = identifier.contains("@")
                ? userRepository.findByEmail(normalizeEmail(identifier)).orElse(null)
                : userRepository.findByUsername(identifier.toLowerCase(Locale.ROOT)).orElse(null);

        String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);

        if (user == null || !passwordMatches) {
            rateLimiter.recordFailure(clientKey);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "该账户已被停用");
        }

        rateLimiter.recordSuccess(clientKey);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return createSession(user, servletRequest);
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
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), now);
            throw ApiException.unauthorized("REFRESH_REUSED", "检测到异常登录会话，请重新登录");
        }
        if (!stored.getExpiresAt().isAfter(now) || !stored.getUser().isEnabled()) {
            stored.setRevokedAt(now);
            throw ApiException.unauthorized("REFRESH_EXPIRED", "登录状态已过期，请重新登录");
        }

        stored.setRevokedAt(now);
        stored.setLastUsedAt(now);

        User user = stored.getUser();
        String newRawToken = randomToken();
        refreshTokenRepository.save(new RefreshToken(
                user,
                hashToken(newRawToken),
                now.plus(properties.jwt().refreshTtl()),
                clientIp(servletRequest),
                truncate(servletRequest.getHeader("User-Agent"), 512)
        ));

        return authResult(user, newRawToken);
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
    public ApiResponses.User updateProfile(Long userId, AuthRequests.UpdateProfile request) {
        User user = requireUser(userId);
        user.setDisplayName(request.displayName().trim());
        return ApiResponses.User.from(userRepository.save(user));
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
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        userRepository.saveAndFlush(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ApiResponses.Session> sessions(Long userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken == null || currentRefreshToken.isBlank()
                ? null : hashToken(currentRefreshToken);
        return refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now())
                .stream()
                .map(token -> new ApiResponses.Session(
                        token.getId(), token.getIpAddress(), token.getUserAgent(), token.getCreatedAt(),
                        token.getLastUsedAt(), token.getExpiresAt(), token.getTokenHash().equals(currentHash)))
                .toList();
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

    @Scheduled(cron = "0 23 4 * * *")
    @Transactional
    public void deleteStaleSessions() {
        refreshTokenRepository.deleteStale(Instant.now().minus(STALE_SESSION_RETENTION));
    }

    private AuthResult createSession(User user, HttpServletRequest request) {
        String rawToken = randomToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                hashToken(rawToken),
                Instant.now().plus(properties.jwt().refreshTtl()),
                clientIp(request),
                truncate(request.getHeader("User-Agent"), 512)
        );
        refreshTokenRepository.save(refreshToken);
        return authResult(user, rawToken);
    }

    private AuthResult authResult(User user, String rawRefreshToken) {
        JwtService.IssuedToken accessToken = jwtService.issue(user);
        long expiresIn = Math.max(0, Duration.between(Instant.now(), accessToken.expiresAt()).toSeconds());
        ApiResponses.Auth auth = new ApiResponses.Auth(
                accessToken.value(), "Bearer", expiresIn, ApiResponses.User.from(user));
        return new AuthResult(auth, rawRefreshToken);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "用户不存在"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
}
