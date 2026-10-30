package neko.fantasy.auth.oauth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OAuthScopes {

    public static final String OPENID = "openid";
    public static final String PROFILE = "profile";
    public static final String EMAIL = "email";
    public static final String OFFLINE_ACCESS = "offline_access";
    public static final String ROLES = "roles";

    private static final Map<String, Definition> CATALOG = Map.of(
            OPENID, new Definition(OPENID, "OpenID 身份", "确认你的梦幻网络身份，并签发 ID Token。"),
            PROFILE, new Definition(PROFILE, "基本资料", "读取昵称、用户名与账户创建时间。"),
            EMAIL, new Definition(EMAIL, "邮箱地址", "读取你的邮箱与验证状态。"),
            ROLES, new Definition(ROLES, "阵营身份", "读取你在梦幻网络中的用户或管理员角色。"),
            OFFLINE_ACCESS, new Definition(OFFLINE_ACCESS, "保持登录", "允许应用在你离线时刷新访问令牌。")
    );

    private static final List<Definition> ORDERED = List.of(
            CATALOG.get(OPENID), CATALOG.get(PROFILE), CATALOG.get(EMAIL),
            CATALOG.get(ROLES), CATALOG.get(OFFLINE_ACCESS));

    private OAuthScopes() {
    }

    public static LinkedHashSet<String> parseRequested(String scope) {
        String value = scope == null || scope.isBlank() ? OPENID : scope.trim();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String part : value.split("\\s+")) {
            if (!CATALOG.containsKey(part)) {
                throw OAuthException.invalidScope("不支持权限范围：" + part);
            }
            result.add(part);
        }
        return result;
    }

    public static void validateRegistered(Set<String> scopes) {
        if (scopes.isEmpty()) {
            throw OAuthException.invalidScope("客户端至少需要声明一个权限范围");
        }
        for (String scope : scopes) {
            if (!CATALOG.containsKey(scope)) {
                throw OAuthException.invalidScope("不支持权限范围：" + scope);
            }
        }
    }

    public static List<Definition> describe(Set<String> scopes) {
        List<Definition> descriptions = new ArrayList<>();
        for (Definition definition : ORDERED) {
            if (scopes.contains(definition.name())) {
                descriptions.add(definition);
            }
        }
        return descriptions;
    }

    public static List<Definition> all() {
        return ORDERED;
    }

    public static String encode(Set<String> scopes) {
        return String.join(" ", scopes);
    }

    public record Definition(String name, String label, String description) {
    }
}
