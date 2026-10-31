package neko.fantasy.auth.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import neko.fantasy.auth.domain.User;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "oauth_refresh_tokens", indexes = {
        @Index(name = "idx_oauth_refresh_active", columnList = "user_id, client_id, revoked_at, expires_at"),
        @Index(name = "idx_oauth_refresh_expires", columnList = "expires_at")
})
public class OAuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OAuthClient client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 512)
    private String scopes;

    @Column(name = "auth_time")
    private Instant authTime;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "family_id", length = 64)
    private String familyId;

    protected OAuthRefreshToken() {
    }

    public OAuthRefreshToken(String tokenHash, OAuthClient client, User user, String scopes,
                             Instant expiresAt) {
        this(tokenHash, client, user, scopes, null, expiresAt);
    }

    public OAuthRefreshToken(String tokenHash, OAuthClient client, User user, String scopes,
                             Instant authTime, Instant expiresAt) {
        this(tokenHash, client, user, scopes, authTime, expiresAt, null);
    }

    public OAuthRefreshToken(String tokenHash, OAuthClient client, User user, String scopes,
                             Instant authTime, Instant expiresAt, String familyId) {
        this.tokenHash = tokenHash;
        this.client = client;
        this.user = user;
        this.scopes = scopes;
        this.authTime = authTime;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
        this.familyId = familyId;
    }

    public OAuthClient getClient() {
        return client;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public User getUser() {
        return user;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public String getFamilyId() {
        return familyId;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean wasRotated() {
        return revokedAt != null && lastUsedAt.isAfter(createdAt);
    }
}
