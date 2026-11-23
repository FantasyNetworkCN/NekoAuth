package neko.fantasy.auth.security;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void directModeNeverTrustsForwardedHeaders() {
        assertThat(ClientIpResolver.resolve("198.51.100.10", "direct",
                headers(Map.of("X-Real-IP", "203.0.113.9"))))
                .isEqualTo("198.51.100.10");
    }

    @Test
    void autoModeUsesProxyHeaderWhenPeerIsLocal() {
        assertThat(ClientIpResolver.resolve("127.0.0.1", "auto",
                headers(Map.of(
                        "X-Forwarded-For", "198.51.100.20, 127.0.0.1",
                        "X-Real-IP", "127.0.0.1"))))
                .isEqualTo("198.51.100.20");
    }

    @Test
    void autoModeIgnoresForwardedHeadersForPublicPeer() {
        assertThat(ClientIpResolver.resolve("198.51.100.10", "auto",
                headers(Map.of("X-Forwarded-For", "203.0.113.9"))))
                .isEqualTo("198.51.100.10");
    }

    private static java.util.function.Function<String, String> headers(Map<String, String> values) {
        return values::get;
    }
}
