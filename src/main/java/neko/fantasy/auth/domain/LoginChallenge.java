package neko.fantasy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "login_challenges", indexes = {
        @Index(name = "idx_login_challenges_user", columnList = "user_id, created_at"),
        @Index(name = "idx_login_challenges_expires", columnList = "expires_at")
})
public class LoginChallenge {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "risk_reasons", length = 1000)
    private String riskReasons;

    @Column(nullable = false, length = 16)
    private String method;

    @Column(nullable = false, length = 254)
    private String target;

    @Lob
    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "auth_time", nullable = false)
    private Instant authTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    protected LoginChallenge() {
    }

    public LoginChallenge(String id, User user, int riskScore, String riskLevel, String riskReasons,
                          String method, String target, String deviceInfo, Instant authTime,
                          Instant now, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.riskReasons = riskReasons;
        this.method = method;
        this.target = target;
        this.deviceInfo = deviceInfo;
        this.authTime = authTime;
        this.createdAt = now;
        this.lastSentAt = now;
        this.expiresAt = expiresAt;
    }

    public void changeMethod(String method, String target, Instant lastSentAt, Instant expiresAt) {
        this.method = method;
        this.target = target;
        this.lastSentAt = lastSentAt;
        this.expiresAt = expiresAt;
    }

    public int recordFailedAttempt() {
        return ++attempts;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getRiskReasons() {
        return riskReasons;
    }

    public String getMethod() {
        return method;
    }

    public String getTarget() {
        return target;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public Instant getAuthTime() {
        return authTime;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getAttempts() {
        return attempts;
    }
}
