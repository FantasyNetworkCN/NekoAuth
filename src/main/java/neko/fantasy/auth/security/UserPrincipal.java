package neko.fantasy.auth.security;

import neko.fantasy.auth.domain.Role;

import java.time.Instant;

public record UserPrincipal(Long id, String username, Role role, Instant authenticatedAt,
                            String sessionFamilyId) {

    public UserPrincipal(Long id, String username, Role role) {
        this(id, username, role, Instant.now(), null);
    }
}
