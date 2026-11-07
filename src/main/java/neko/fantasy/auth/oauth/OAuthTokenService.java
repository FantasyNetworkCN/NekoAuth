package neko.fantasy.auth.oauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import neko.fantasy.auth.config.OAuthProperties;
import neko.fantasy.auth.domain.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class OAuthTokenService {

    private final OAuthProperties properties;
    private final OAuthKeyService keyService;

    public OAuthTokenService(OAuthProperties properties, OAuthKeyService keyService) {
        this.properties = properties;
        this.keyService = keyService;
    }

    public IssuedAccessToken issueAccessToken(User user, String clientId, Set<String> scopes) {
        return issueAccessToken(user, clientId, scopes, properties.accessTokenTtl());
    }

    public IssuedAccessToken issueAccessToken(User user, String clientId, Set<String> scopes,
                                              Duration ttl) {
        return issueAccessToken(user, clientId, scopes, ttl, null);
    }

    public IssuedAccessToken issueAccessToken(User user, String clientId, Set<String> scopes,
                                              Duration ttl, String sessionFamilyId) {
        Instant expiresAt = Instant.now().plus(ttl);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "oauth_access");
        claims.put("client_id", clientId);
        claims.put("scope", OAuthScopes.encode(scopes));
        claims.put("ver", user.getTokenVersion());
        if (sessionFamilyId != null && !sessionFamilyId.isBlank()) {
            claims.put("sid", sessionFamilyId);
        } else {
            // 无浏览器会话的内部测试/兼容令牌显式标记为 standalone；旧令牌没有标记会被视为失效。
            claims.put("session_mode", "standalone");
        }
        if (scopes.contains(OAuthScopes.PROFILE)) {
            claims.put("username", user.getUsername());
        }
        if (scopes.contains(OAuthScopes.EMAIL)) {
            claims.put("email", user.getEmail());
        }
        if (scopes.contains(OAuthScopes.ROLES)) {
            claims.put("role", user.getRole().name());
        }
        String value = keyService.issue(claims, user.getId().toString(), ttl);
        return new IssuedAccessToken(value, expiresAt);
    }

    public String issueIdToken(User user, String clientId, Set<String> scopes, String nonce,
                               String accessToken) {
        Instant authenticatedAt = user.getLastLoginAt() == null ? Instant.now() : user.getLastLoginAt();
        return issueIdToken(user, clientId, scopes, nonce, accessToken, authenticatedAt);
    }

    public String issueIdToken(User user, String clientId, Set<String> scopes, String nonce,
                               String accessToken, Instant authenticatedAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", clientId);
        claims.put("azp", clientId);
        claims.put("auth_time", authenticatedAt.getEpochSecond());
        claims.put("at_hash", leftHalfHash(accessToken));
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        if (scopes.contains(OAuthScopes.PROFILE)) {
            claims.put("preferred_username", user.getUsername());
            claims.put("name", user.publicName());
            claims.put("picture", keyService.getIssuer()
                    + neko.fantasy.auth.service.AvatarService.avatarUrl(user.getId()));
        }
        if (scopes.contains(OAuthScopes.EMAIL)) {
            claims.put("email", user.getEmail());
        }
        if (scopes.contains(OAuthScopes.ROLES)) {
            claims.put("role", user.getRole().name());
        }
        return keyService.issue(claims, user.getId().toString(), properties.idTokenTtl());
    }

    public IdTokenHint parseIdTokenHint(String token) throws JwtException {
        Claims claims = keyService.parseForLogout(token);
        Set<String> audience = claims.getAudience();
        if (audience == null || audience.isEmpty()) {
            throw new JwtException("ID token audience is missing");
        }
        String clientId = claims.get("azp", String.class);
        if (clientId == null) {
            if (audience.size() != 1) {
                throw new JwtException("ID token azp is required for multiple audiences");
            }
            clientId = audience.iterator().next();
        } else if (!audience.contains(clientId)) {
            throw new JwtException("ID token azp is not included in audience");
        }
        try {
            Long userId = Long.parseLong(claims.getSubject());
            Number authTime = claims.get("auth_time", Number.class);
            Instant authenticatedAt = authTime == null
                    ? (claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant())
                    : Instant.ofEpochSecond(authTime.longValue());
            if (authenticatedAt == null) {
                throw new JwtException("ID token auth_time and iat are missing");
            }
            return new IdTokenHint(userId, clientId, authenticatedAt);
        } catch (RuntimeException exception) {
            throw new JwtException("Invalid ID token hint claims", exception);
        }
    }

    public AccessTokenClaims parseAccessToken(String token) throws JwtException {
        Claims claims = keyService.parse(token);
        if (!"oauth_access".equals(claims.get("typ", String.class))) {
            throw new JwtException("Invalid token type");
        }
        try {
            return new AccessTokenClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get("client_id", String.class),
                    claims.get("scope", String.class),
                    claims.get("username", String.class),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    claims.get("ver", Integer.class),
                    claims.get("sid", String.class),
                    "standalone".equals(claims.get("session_mode", String.class)),
                    claims.getExpiration().toInstant(),
                    claims.getIssuedAt().toInstant(),
                    claims.getId()
            );
        } catch (RuntimeException exception) {
            throw new JwtException("Invalid OAuth access token claims", exception);
        }
    }

    public String issuer() {
        return keyService.getIssuer();
    }

    public OAuthProperties properties() {
        return properties;
    }

    private static String leftHalfHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            byte[] half = new byte[16];
            System.arraycopy(digest, 0, half, 0, half.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(half);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }

    public record AccessTokenClaims(Long userId, String clientId, String scope, String username,
                                    String email, String role,
                                    int tokenVersion, String sessionFamilyId, boolean standalone,
                                    Instant expiresAt, Instant issuedAt, String jti) {
    }

    public record IdTokenHint(Long userId, String clientId, Instant authenticatedAt) {
    }
}
