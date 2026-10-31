package neko.fantasy.auth.domain;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_user_active", columnList = "user_id, revoked_at, expires_at"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id, revoked_at"),
        @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

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

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_info", length = 2000)
    private String deviceInfo;

    @Column(name = "family_id", length = 64)
    private String familyId;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, String ipAddress, String userAgent) {
        this(user, tokenHash, Instant.now(), expiresAt, ipAddress, userAgent);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent) {
        this(user, tokenHash, authTime, expiresAt, ipAddress, userAgent, null);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent, String deviceInfo) {
        this(user, tokenHash, authTime, expiresAt, ipAddress, userAgent, deviceInfo, null);
    }

    public RefreshToken(User user, String tokenHash, Instant authTime, Instant expiresAt,
                        String ipAddress, String userAgent, String deviceInfo, String familyId) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.authTime = authTime;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceInfo = deviceInfo;
        this.familyId = familyId;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public String getFamilyId() {
        return familyId;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
