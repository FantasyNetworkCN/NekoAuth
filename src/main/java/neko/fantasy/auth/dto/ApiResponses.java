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
            String nickname = user.getRole() == Role.ADMIN ? null : user.getDisplayName();
            return new User(user.getId(), user.getUsername(), nickname, user.getEmail(),
                    user.publicName(), user.getRole(), user.isEnabled(), user.getCreatedAt(),
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

    public record VerificationCodeSent(String message, long expiresInSeconds, long resendAfterSeconds) {
    }

    public record RegistrationCaptcha(String id, String image, long expiresInSeconds) {
    }

    public record Error(Instant timestamp, int status, String code, String message,
                        Map<String, String> fields) {
    }
}
