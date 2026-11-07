package neko.fantasy.auth.internal;

import io.jsonwebtoken.JwtException;
import neko.fantasy.auth.config.OAuthProperties;
import neko.fantasy.auth.domain.User;
import neko.fantasy.auth.exception.ApiException;
import neko.fantasy.auth.oauth.OAuthClient;
import neko.fantasy.auth.oauth.OAuthRevokedAccessTokenRepository;
import neko.fantasy.auth.oauth.OAuthScopes;
import neko.fantasy.auth.oauth.OAuthTokenService;
import neko.fantasy.auth.repository.RefreshTokenRepository;
import neko.fantasy.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** 为可信第一方服务签发 NekoAuth 所有的长期访问令牌。 */
@Service
public class InternalTokenService {

    private static final Duration DEFAULT_BRIDGE_TTL = Duration.ofDays(365);

    private final OAuthTokenService tokenService;
    private final OAuthProperties properties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository browserRefreshTokenRepository;
    private final OAuthRevokedAccessTokenRepository revokedAccessTokenRepository;

    public InternalTokenService(OAuthTokenService tokenService, OAuthProperties properties,
                                UserRepository userRepository,
                                RefreshTokenRepository browserRefreshTokenRepository,
                                OAuthRevokedAccessTokenRepository revokedAccessTokenRepository) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.userRepository = userRepository;
        this.browserRefreshTokenRepository = browserRefreshTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
    }

    public IssuedToken issue(User user, OAuthClient client) {
        return issue(user, client, null);
    }

    public IssuedToken issue(User user, OAuthClient client, String sessionFamilyId) {
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "该账户已被停用");
        }
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(OAuthScopes.ACCOUNT_BRIDGE);
        if (client.getScopes().contains(OAuthScopes.EMAIL)) {
            scopes.add(OAuthScopes.EMAIL);
        }
        if (client.getScopes().contains(OAuthScopes.PROFILE)) {
            scopes.add(OAuthScopes.PROFILE);
        }
        if (client.getScopes().contains(OAuthScopes.ROLES)) {
            scopes.add(OAuthScopes.ROLES);
        }
        Duration ttl = properties.bridgeTokenTtl() == null ? DEFAULT_BRIDGE_TTL : properties.bridgeTokenTtl();
        OAuthTokenService.IssuedAccessToken token = tokenService.issueAccessToken(
                user, client.getClientId(), scopes, ttl, sessionFamilyId);
        long expiresIn = Math.max(0, Duration.between(Instant.now(), token.expiresAt()).toSeconds());
        return new IssuedToken(token.value(), "Bearer", expiresIn);
    }

    public IssuedToken exchange(OAuthClient client, String rawAccessToken) {
        OAuthTokenService.AccessTokenClaims claims;
        try {
            claims = tokenService.parseAccessToken(rawAccessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw ApiException.unauthorized("INVALID_TOKEN", "NekoAuth 访问令牌无效或已过期");
        }
        if (!client.getClientId().equals(claims.clientId())) {
            throw ApiException.unauthorized("INVALID_TOKEN", "访问令牌不属于当前客户端");
        }
        if (claims.jti() == null || claims.jti().isBlank()
                || revokedAccessTokenRepository.existsById(claims.jti())) {
            throw ApiException.unauthorized("INVALID_TOKEN", "用户会话已失效");
        }
        if (!claims.standalone()
                && (claims.sessionFamilyId() == null || claims.sessionFamilyId().isBlank()
                || !browserRefreshTokenRepository
                .existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(
                        claims.sessionFamilyId(), Instant.now()))) {
            throw ApiException.unauthorized("INVALID_TOKEN", "用户会话已失效");
        }
        User user = userRepository.findById(claims.userId())
                .filter(User::isEnabled)
                .filter(candidate -> candidate.getTokenVersion() == claims.tokenVersion())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "用户会话已失效"));
        return issue(user, client, claims.sessionFamilyId());
    }

    public record IssuedToken(String accessToken, String tokenType, long expiresIn) {
    }
}
