package neko.fantasy.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import neko.fantasy.auth.service.AuthCookieService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
public class OAuthLogoutController {

    private final OAuthLogoutService logoutService;
    private final AuthCookieService cookieService;

    public OAuthLogoutController(OAuthLogoutService logoutService, AuthCookieService cookieService) {
        this.logoutService = logoutService;
        this.cookieService = cookieService;
    }

    @GetMapping("/oauth2/logout")
    public ResponseEntity<?> logoutGet(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return logout(idTokenHint, clientId, postLogoutRedirectUri, state, false, request);
    }

    @PostMapping("/oauth2/logout")
    public ResponseEntity<?> logoutPost(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return logout(idTokenHint, clientId, postLogoutRedirectUri, state, false, request);
    }

    /** 用户在确认页明确提交后才执行无 hint 或 hint 与当前会话不匹配的登出。 */
    @PostMapping("/oauth2/logout/confirm")
    public ResponseEntity<?> logoutConfirm(
            @RequestParam(name = "id_token_hint", required = false) String idTokenHint,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "post_logout_redirect_uri", required = false) String postLogoutRedirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        return logout(idTokenHint, clientId, postLogoutRedirectUri, state, true, request);
    }

    private ResponseEntity<?> logout(String idTokenHint, String clientId,
                                     String postLogoutRedirectUri, String state,
                                     boolean confirmed, HttpServletRequest request) {
        OAuthLogoutService.LogoutParameters parameters = new OAuthLogoutService.LogoutParameters(
                idTokenHint, clientId, postLogoutRedirectUri, state);
        String refreshToken = cookieService.read(request).orElse(null);

        if (!confirmed) {
            OAuthLogoutService.LogoutPlan plan = logoutService.plan(parameters, refreshToken);
            if (plan.confirmationRequired()) {
                return redirect(confirmationTarget(parameters, plan.confirmationReason()), false);
            }
        }

        String target = logoutService.complete(parameters, refreshToken);
        return redirect(target, true);
    }

    private static String confirmationTarget(OAuthLogoutService.LogoutParameters parameters,
                                             OAuthLogoutService.ConfirmationReason reason) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/logout/confirm");
        addIfPresent(builder, "id_token_hint", parameters.idTokenHint());
        addIfPresent(builder, "client_id", parameters.clientId());
        addIfPresent(builder, "post_logout_redirect_uri", parameters.postLogoutRedirectUri());
        addIfPresent(builder, "state", parameters.state());
        addIfPresent(builder, "reason", reason == null ? null : reason.code());
        return builder.build().encode().toUriString();
    }

    private static void addIfPresent(UriComponentsBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value);
        }
    }

    private ResponseEntity<?> redirect(String target, boolean clearCookies) {
        if (clearCookies && isExternalHttpTarget(target)) {
            String escaped = htmlEscape(target);
            String body = """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head>
                      <meta charset="utf-8">
                      <meta name="referrer" content="no-referrer">
                      <meta http-equiv="refresh" content="0;url=%s">
                      <title>正在退出</title>
                    </head>
                    <body>
                      <p>已退出 NekoAuth，正在返回应用…</p>
                      <p><a href="%s">如果没有自动跳转，请点击这里</a></p>
                    </body>
                    </html>
                    """.formatted(escaped, escaped);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("Referrer-Policy", "no-referrer")
                    .header(HttpHeaders.SET_COOKIE,
                            cookieService.clearHeaders().toArray(String[]::new))
                    .body(body);
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer");
        if (clearCookies) {
            response.header(HttpHeaders.SET_COOKIE,
                    cookieService.clearHeaders().toArray(String[]::new));
        }
        return response.build();
    }

    private static boolean isExternalHttpTarget(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(target);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            return uri.getHost() != null && ("https".equals(scheme) || "http".equals(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
