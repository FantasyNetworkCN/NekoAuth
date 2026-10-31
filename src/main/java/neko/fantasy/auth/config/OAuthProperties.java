package neko.fantasy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        String issuer,
        String keyFile,
        Duration authorizationCodeTtl,
        Duration accessTokenTtl,
        Duration bridgeTokenTtl,
        Duration idTokenTtl,
        Duration refreshTokenTtl
) {
}
