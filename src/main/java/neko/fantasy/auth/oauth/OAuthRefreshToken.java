package neko.fantasy.auth.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import neko.fantasy.auth.domain.User;

import java.time.Instant;

@Entity
@Table(name = "oauth_refresh_tokens")
public class OAuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private OAuthClient client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 512)
    private String scopes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OAuthRefreshToken() {
    }

    public OAuthRefreshToken(String tokenHash, OAuthClient client, User user, String scopes,
                             Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.client = client;
        this.user = user;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
    }

    public OAuthClient getClient() {
        return client;
    }

    public User getUser() {
        return user;
    }

    public String getScopes() {
        return scopes;
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
