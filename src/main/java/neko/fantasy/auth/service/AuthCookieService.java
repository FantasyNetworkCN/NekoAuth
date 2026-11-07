package neko.fantasy.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class AuthCookieService {

    public static final String SESSION_HINT_COOKIE = "nekoauth_session_hint";
    public static final String SSO_SESSION_COOKIE = "nekoauth_sso_session";
    private static final String COOKIE_PATH = "/";
    private static final String LEGACY_COOKIE_PATH = "/api/auth";

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String value) {
        return build(value, properties.jwt().refreshTtl(), COOKIE_PATH);
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO, COOKIE_PATH);
    }

    /**
     * 写入刷新令牌，并清理旧版本只作用于 {@code /api/auth} 的同名 Cookie。
     * OIDC RP-Initiated Logout 需要读取浏览器会话，因此 Cookie 必须覆盖全站路径。
     */
    public List<String> createHeaders(String value, String sessionId) {
        List<String> headers = new ArrayList<>(3);
        headers.add(build(value, properties.jwt().refreshTtl(), COOKIE_PATH).toString());
        headers.add(build("", Duration.ZERO, LEGACY_COOKIE_PATH).toString());
        if (hasHintDomain() && sessionId != null && !sessionId.isBlank()) {
            headers.add(buildHint(sessionHint(sessionId), properties.jwt().refreshTtl()).toString());
            headers.add(buildSsoSession(sessionId, properties.jwt().refreshTtl()).toString());
        }
        return List.copyOf(headers);
    }

    public List<String> clearHeaders() {
        List<String> headers = new ArrayList<>(3);
        headers.add(build("", Duration.ZERO, COOKIE_PATH).toString());
        headers.add(build("", Duration.ZERO, LEGACY_COOKIE_PATH).toString());
        if (hasHintDomain()) {
            headers.add(buildHint("", Duration.ZERO).toString());
            headers.add(buildSsoSession("", Duration.ZERO).toString());
        }
        return List.copyOf(headers);
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        // 升级部署时可能同时存在旧路径和新路径的同名 Cookie。浏览器按路径长度排序，
        // 更长（更具体）的旧路径 Cookie 会先出现，因此选择最后一个非空值。
        return Arrays.stream(cookies)
                .filter(cookie -> properties.cookie().name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .reduce((first, second) -> second);
    }

    public Optional<String> readSsoSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> SSO_SESSION_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge, String path) {
        return ResponseCookie.from(properties.cookie().name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    /**
     * 跨子域登录提示：只说明当前浏览器是否持有 Auth 会话，不包含可用的认证凭据。
     * 第一方 Web 子域据此检测登录切换或退出；刷新令牌仍保持 host-only。
     */
    private ResponseCookie buildHint(String value, Duration maxAge) {
        return ResponseCookie.from(SESSION_HINT_COOKIE, value)
                .httpOnly(false)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .domain(properties.cookie().domain().trim())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie buildSsoSession(String value, Duration maxAge) {
        return ResponseCookie.from(SSO_SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .domain(properties.cookie().domain().trim())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private boolean hasHintDomain() {
        return properties.cookie().domain() != null && !properties.cookie().domain().isBlank();
    }

    static String sessionHint(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
