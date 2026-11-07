package neko.fantasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.service.AuthCookieService;
import neko.fantasy.auth.service.AuthService;
import neko.fantasy.auth.service.AvatarService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第一方父域共享会话。只接受 Auth 自己写入的 HttpOnly 会话 Cookie，
 * 不使用 OAuth 授权码，也不接受客户端 Basic 凭据。
 */
@RestController
@RequestMapping("/api/auth/shared-session")
public class SharedSessionController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public SharedSessionController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping
    public Account resolve(HttpServletRequest request) {
        String sessionFamilyId = cookieService.readSsoSession(request)
                .orElseThrow(() -> ApiException.unauthorized(
                        "SHARED_SESSION_REQUIRED", "共享登录会话不存在"));
        User user = authService.findUserBySsoSession(sessionFamilyId)
                .orElseThrow(() -> ApiException.unauthorized(
                        "SHARED_SESSION_INVALID", "共享登录会话已失效"));
        return Account.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookieService.readSsoSession(request).ifPresent(authService::revokeSsoSession);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        cookieService.clearHeaders().toArray(String[]::new))
                .build();
    }

    public record Account(long id, String email, String username, String nickname,
                          String role, boolean enabled, String avatarUrl) {
        static Account from(User user) {
            return new Account(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.publicName(),
                    user.getRole().name(),
                    user.isEnabled(),
                    AvatarService.avatarUrl(user.getId()));
        }
    }
}
