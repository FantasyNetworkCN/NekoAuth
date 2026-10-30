package neko.fantasy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.service.AuthCookieService;
import neko.fantasy.auth.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class OAuthLogoutController {

    private final OAuthLogoutService logoutService;
    private final AuthCookieService cookieService;
    private final AuthService authService;

    public OAuthLogoutController(OAuthLogoutService logoutService, AuthCookieService cookieService,
                                 AuthService authService) {
        this.logoutService = logoutService;
        this.cookieService = cookieService;
        this.authService = authService;
    }

    @GetMapping("/oauth2/logout")
    public ResponseEntity<Void> logoutGet(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return logout(idTokenHint, clientId, postLogoutRedirectUri, state, request);
    }

    @PostMapping("/oauth2/logout")
    public ResponseEntity<Void> logoutPost(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return logout(idTokenHint, clientId, postLogoutRedirectUri, state, request);
    }

    private ResponseEntity<Void> logout(String idTokenHint, String clientId,
                                        String postLogoutRedirectUri, String state,
                                        HttpServletRequest request) {
        String target = logoutService.logout(idTokenHint, clientId, postLogoutRedirectUri, state);
        authService.logout(cookieService.read(request).orElse(null));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }
}
