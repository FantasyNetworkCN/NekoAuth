package neko.fantasy.auth.internal;

import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.oauth.OAuthClientRepository;
import neko.fantasy.auth.oauth.OAuthScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 服务端账号桥接的客户端认证。
 *
 * <p>只接受已启用、机密、且登记了 {@link OAuthScopes#ACCOUNT_BRIDGE} 专用 scope 的 OAuth
 * 客户端，使用 HTTP Basic。client_id / secret 不在 Auth 代码中写死，由部署方在 Auth
 * 注册客户端后填入 NekoMusic 的 OAuth 设置。普通 OAuth 客户端、公共客户端以及浏览器
 * JWT 都不能调用内部账号接口。</p>
 */
@Component
public class InternalClientAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(InternalClientAuthenticator.class);

    private final OAuthClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public InternalClientAuthenticator(OAuthClientRepository clientRepository,
                                       PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public OAuthClient requireClient(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw ApiException.unauthorized("INVALID_CLIENT", "账号桥接需要 HTTP Basic 客户端认证");
        }

        String clientId;
        String secret;
        try {
            String decoded = new String(Base64.getDecoder().decode(
                    authorizationHeader.substring(6).trim()), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) {
                throw new IllegalArgumentException("missing colon");
            }
            clientId = URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8);
            secret = URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw ApiException.unauthorized("INVALID_CLIENT", "HTTP Basic 客户端认证格式不正确");
        }

        OAuthClient client = clientRepository.findByClientId(clientId).orElse(null);
        if (client == null || !client.isEnabled()) {
            log.warn("账号桥接客户端认证失败: client_id={} reason=not_found_or_disabled",
                    loggableClientId(clientId));
            throw ApiException.unauthorized("INVALID_CLIENT", "客户端不存在或已停用");
        }
        if (client.isPublicClient()) {
            log.warn("账号桥接客户端认证失败: client_id={} reason=public_client",
                    loggableClientId(clientId));
            throw ApiException.unauthorized("INVALID_CLIENT", "公共客户端不能调用账号桥接");
        }
        if (!passwordEncoder.matches(secret, client.getClientSecretHash())) {
            log.warn("账号桥接客户端认证失败: client_id={} reason=secret_mismatch",
                    loggableClientId(clientId));
            throw ApiException.unauthorized("INVALID_CLIENT", "客户端密钥不正确");
        }
        if (!client.getScopes().contains(OAuthScopes.ACCOUNT_BRIDGE)) {
            log.warn("账号桥接客户端认证失败: client_id={} reason=missing_account_bridge_scope",
                    loggableClientId(clientId));
            throw ApiException.unauthorized("INVALID_CLIENT", "客户端未获准调用账号桥接");
        }
        return client;
    }

    private static String loggableClientId(String clientId) {
        String sanitized = clientId.replaceAll("[^A-Za-z0-9._-]", "?");
        return sanitized.length() <= 64 ? sanitized : sanitized.substring(0, 64);
    }
}
