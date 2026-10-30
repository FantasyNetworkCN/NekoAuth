package neko.fantasy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        Jwt jwt,
        Cookie cookie,
        Cors cors,
        BootstrapAdmin bootstrapAdmin,
        RateLimit rateLimit,
        Setup setup
) {

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookie(String name, boolean secure, String sameSite) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record BootstrapAdmin(String username, String email, String displayName, String password) {
    }

    public record RateLimit(int attempts, Duration window) {
    }

    public record Setup(String stateDir, String token) {
    }
}
