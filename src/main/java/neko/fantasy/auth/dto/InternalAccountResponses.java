package neko.fantasy.auth.dto;

import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;

/** 服务端账号桥接的最小账号快照；不包含密码哈希或任何会话令牌。 */
public final class InternalAccountResponses {

    private InternalAccountResponses() {
    }

    public record Account(long id, String email, String username, Role role, boolean enabled) {

        public static Account from(User user) {
            return new Account(user.getId(), user.getEmail(), user.getUsername(),
                    user.getRole(), user.isEnabled());
        }
    }
}
