package neko.fantasy.auth.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** 已主动吊销的 OAuth access token；仅保存 JWT ID，不保存令牌原文。 */
@Entity
@Table(name = "oauth_revoked_access_tokens", indexes = {
        @Index(name = "idx_oauth_revoked_access_expires", columnList = "expires_at")
})
public class OAuthRevokedAccessToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected OAuthRevokedAccessToken() {
    }

    public OAuthRevokedAccessToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    public String getJti() {
        return jti;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
