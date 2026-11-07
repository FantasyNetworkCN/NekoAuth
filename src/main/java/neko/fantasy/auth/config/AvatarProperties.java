package neko.fantasy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "avatar")
public record AvatarProperties(String directory, long maxBytes) {
}
