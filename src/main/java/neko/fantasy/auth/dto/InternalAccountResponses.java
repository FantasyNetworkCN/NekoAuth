package neko.fantasy.auth.dto;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;

/** 服务端账号桥接的账号快照；令牌只能由 NekoAuth 签发。 */
public final class InternalAccountResponses {

    private InternalAccountResponses() {
    }

    public record Account(long id, String email, String username, Role role, boolean enabled,
                          String accessToken, String tokenType, Long expiresIn) {

        public static Account from(User user) {
            return new Account(user.getId(), user.getEmail(), user.getUsername(),
                    user.getRole(), user.isEnabled(), null, null, null);
        }

        public static Account from(User user, neko.fantasy.auth.internal.InternalTokenService.IssuedToken token) {
            return new Account(user.getId(), user.getEmail(), user.getUsername(),
                    user.getRole(), user.isEnabled(), token.accessToken(), token.tokenType(),
                    token.expiresIn());
        }
    }
}
