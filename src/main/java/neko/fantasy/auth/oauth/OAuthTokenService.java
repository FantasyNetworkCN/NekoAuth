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
        Instant expiresAt = Instant.now().plus(properties.accessTokenTtl());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("typ", "oauth_access");
        claims.put("client_id", clientId);
        claims.put("scope", OAuthScopes.encode(scopes));
        claims.put("ver", user.getTokenVersion());
        if (scopes.contains(OAuthScopes.PROFILE)) {
            claims.put("username", user.getUsername());
        }
        if (scopes.contains(OAuthScopes.ROLES)) {
            claims.put("role", user.getRole().name());
        }
        String value = keyService.issue(claims, user.getId().toString(), properties.accessTokenTtl());
        return new IssuedAccessToken(value, expiresAt);
    }

    public String issueIdToken(User user, String clientId, Set<String> scopes, String nonce,
                               String accessToken) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", clientId);
        claims.put("azp", clientId);
        claims.put("auth_time", user.getLastLoginAt() == null
                ? Instant.now().getEpochSecond() : user.getLastLoginAt().getEpochSecond());
        claims.put("at_hash", leftHalfHash(accessToken));
        if (nonce != null && !nonce.isBlank()) {
            claims.put("nonce", nonce);
        }
        if (scopes.contains(OAuthScopes.PROFILE)) {
            claims.put("preferred_username", user.getUsername());
            claims.put("name", user.getDisplayName());
        }
        if (scopes.contains(OAuthScopes.EMAIL)) {
            claims.put("email", user.getEmail());
            claims.put("email_verified", true);
        }
        if (scopes.contains(OAuthScopes.ROLES)) {
            claims.put("role", user.getRole().name());
        }
        return keyService.issue(claims, user.getId().toString(), properties.idTokenTtl());
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
                    claims.get("ver", Integer.class),
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
                                    int tokenVersion, Instant expiresAt, Instant issuedAt, String jti) {
    }
}
