package neko.fantasy.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import neko.fantasy.auth.config.AuthProperties;
import neko.fantasy.auth.domain.Role;
import neko.fantasy.auth.domain.User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE = "access";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(AuthProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("auth.jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.jwt().issuer();
        this.accessTtl = properties.jwt().accessTtl();
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTtl);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("typ", TOKEN_TYPE)
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("ver", user.getTokenVersion())
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public AccessClaims parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!TOKEN_TYPE.equals(claims.get("typ", String.class))) {
            throw new JwtException("Invalid token type");
        }

        try {
            return new AccessClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class),
                    Role.valueOf(claims.get("role", String.class)),
                    claims.get("ver", Integer.class),
                    claims.getExpiration().toInstant()
            );
        } catch (RuntimeException exception) {
            throw new JwtException("Invalid token claims", exception);
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record AccessClaims(Long userId, String username, Role role, int tokenVersion,
                               Instant expiresAt) {
    }
}
