package neko.fantasy.auth.dto;

import neko.fantasy.auth.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiResponses {

    private ApiResponses() {
    }

    public record User(Long id, String username, String nickname, String email, String displayName, Role role,
                       boolean enabled, Instant createdAt, Instant updatedAt, Instant lastLoginAt) {

        public static User from(neko.fantasy.auth.domain.User user) {
            return new User(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                    user.getDisplayName(), user.getRole(), user.isEnabled(), user.getCreatedAt(),
                    user.getUpdatedAt(), user.getLastLoginAt());
        }
    }

    public record Auth(String accessToken, String tokenType, long expiresIn, User user) {
    }

    public record Session(Long id, String ipAddress, String userAgent, Instant createdAt,
                          Instant lastUsedAt, Instant expiresAt, boolean current) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record Message(String message) {
    }

    public record Error(Instant timestamp, int status, String code, String message,
                        Map<String, String> fields) {
    }
}
