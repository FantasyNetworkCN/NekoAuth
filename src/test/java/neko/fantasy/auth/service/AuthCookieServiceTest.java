package neko.fantasy.auth.service;

import neko.fantasy.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    @Test
    void refreshCookieStaysHostOnlyWhileSessionHintUsesConfiguredParentDomain() {
        AuthCookieService service = new AuthCookieService(properties("nekocore.cn"));

        List<String> headers = service.createHeaders("refresh-value", "session-family");

        assertThat(headers).hasSize(4);
        assertThat(headers.get(0))
                .contains("refresh_token=refresh-value")
                .doesNotContain("Domain=");
        assertThat(headers.get(2))
                .startsWith(AuthCookieService.SESSION_HINT_COOKIE + "="
                        + AuthCookieService.sessionHint("session-family"))
                .contains("Domain=nekocore.cn");
        assertThat(headers.get(3))
                .startsWith(AuthCookieService.SSO_SESSION_COOKIE + "=session-family")
                .contains("Domain=nekocore.cn")
                .contains("HttpOnly");
    }

    @Test
    void sessionHintIsOmittedWhenParentDomainIsNotConfigured() {
        AuthCookieService service = new AuthCookieService(properties(""));

        List<String> headers = service.createHeaders("refresh-value", "session-family");

        assertThat(headers).hasSize(2);
        assertThat(headers).noneMatch(value -> value.contains(AuthCookieService.SESSION_HINT_COOKIE));
        assertThat(headers).noneMatch(value -> value.contains(AuthCookieService.SSO_SESSION_COOKIE));
    }

    private static AuthProperties properties(String domain) {
        return new AuthProperties(
                new AuthProperties.Jwt("test-secret-at-least-32-bytes-long", "test",
                        Duration.ofMinutes(15), Duration.ofDays(14)),
                new AuthProperties.Cookie("refresh_token", false, "Strict", domain),
                new AuthProperties.Cors(List.of("http://localhost")),
                null,
                null,
                null,
                null);
    }
}
