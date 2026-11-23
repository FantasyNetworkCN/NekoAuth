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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "known_devices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_known_devices_user_token",
                columnNames = {"user_id", "token_hash"}),
        indexes = {
                @Index(name = "idx_known_devices_user_seen", columnList = "user_id, last_seen_at"),
                @Index(name = "idx_known_devices_user_trusted", columnList = "user_id, trusted_until")
        })
public class KnownDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "profile_hash", length = 64)
    private String profileHash;

    @Column(name = "device_type", length = 16)
    private String deviceType;

    @Column(name = "device_platform", length = 40)
    private String devicePlatform;

    @Column(name = "device_browser", length = 40)
    private String deviceBrowser;

    @Column(name = "last_ip_address", length = 64)
    private String lastIpAddress;

    @Column(name = "last_user_agent", length = 512)
    private String lastUserAgent;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "trusted_until")
    private Instant trustedUntil;

    protected KnownDevice() {
    }

    public KnownDevice(User user, String tokenHash, Instant now) {
        this.user = user;
        this.tokenHash = tokenHash;
        firstSeenAt = now;
        lastSeenAt = now;
    }

    public void touch(String profileHash, String deviceType, String devicePlatform, String deviceBrowser,
                      String ipAddress, String userAgent, Instant now) {
        this.profileHash = profileHash;
        this.deviceType = deviceType;
        this.devicePlatform = devicePlatform;
        this.deviceBrowser = deviceBrowser;
        this.lastIpAddress = ipAddress;
        this.lastUserAgent = userAgent;
        this.lastSeenAt = now;
    }

    public void trustAfterStepUp(Instant now, Duration trustTtl) {
        lastVerifiedAt = now;
        trustedUntil = now.plus(trustTtl);
    }

    public boolean isTrusted(Instant now) {
        return trustedUntil != null && trustedUntil.isAfter(now);
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

    public String getProfileHash() {
        return profileHash;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public Instant getTrustedUntil() {
        return trustedUntil;
    }
}
