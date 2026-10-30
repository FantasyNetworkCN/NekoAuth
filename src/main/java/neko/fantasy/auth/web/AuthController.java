package neko.fantasy.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.dto.AuthRequests;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.security.UserPrincipal;
import neko.fantasy.auth.service.AuthCookieService;
import neko.fantasy.auth.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponses.Auth> register(
            @Valid @RequestBody AuthRequests.Register request,
            HttpServletRequest servletRequest
    ) {
        return authResponse(HttpStatus.CREATED, authService.register(request, servletRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponses.Auth> login(
            @Valid @RequestBody AuthRequests.Login request,
            HttpServletRequest servletRequest
    ) {
        return authResponse(HttpStatus.OK, authService.login(request, servletRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponses.Auth> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthService.AuthResult result = authService.refresh(
                    cookieService.read(request).orElse(null), request);
            return authResponse(HttpStatus.OK, result);
        } catch (ApiException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clear().toString());
            throw exception;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookieService.read(request).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public ApiResponses.User me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.id());
    }

    @PatchMapping("/me")
    public void updateProfile() {
        throw ApiException.conflict("PROFILE_IMMUTABLE", "昵称注册后不可修改");
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AuthRequests.ChangePassword request
    ) {
        authService.changePassword(principal.id(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .build();
    }

    @GetMapping("/sessions")
    public List<ApiResponses.Session> sessions(@AuthenticationPrincipal UserPrincipal principal,
                                               HttpServletRequest request) {
        return authService.sessions(principal.id(), cookieService.read(request).orElse(null));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        boolean current = authService.revokeSession(
                principal.id(), id, cookieService.read(request).orElse(null));
        if (current) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        authService.revokeAllSessions(principal.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .build();
    }

    private ResponseEntity<ApiResponses.Auth> authResponse(
            HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieService.create(result.refreshToken()).toString())
                .body(result.auth());
    }
}
