package neko.fantasy.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AuthCookieService {

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
    public List<String> createHeaders(String value) {
        return List.of(
                build(value, properties.jwt().refreshTtl(), COOKIE_PATH).toString(),
                build("", Duration.ZERO, LEGACY_COOKIE_PATH).toString());
    }

    public List<String> clearHeaders() {
        return List.of(
                build("", Duration.ZERO, COOKIE_PATH).toString(),
                build("", Duration.ZERO, LEGACY_COOKIE_PATH).toString());
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

    private ResponseCookie build(String value, Duration maxAge, String path) {
        return ResponseCookie.from(properties.cookie().name(), value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
