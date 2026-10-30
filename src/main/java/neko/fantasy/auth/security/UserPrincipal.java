package neko.fantasy.auth.security;

import neko.fantasy.auth.domain.Role;

public record UserPrincipal(Long id, String username, Role role) {
}
