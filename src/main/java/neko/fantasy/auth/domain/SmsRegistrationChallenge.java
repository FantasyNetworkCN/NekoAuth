package neko.fantasy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "sms_registration_challenges")
public class SmsRegistrationChallenge {

    @Id
    @Column(length = 14)
    private String phone;
    @Column(name = "out_id", length = 36)
    private String outId;
    @Column(name = "binding_user_id")
    private Long bindingUserId;
    @Column(name = "password_reset_user_id")
    private Long passwordResetUserId;
    @Column(name = "login_challenge_id", length = 36)
    private String loginChallengeId;
    private Instant expiresAt;
    @Column(nullable = false)
    private Instant lastSentAt;
    @Column(nullable = false)
    private Instant windowStartedAt;
    private int sendCount;
    private int attempts;

    protected SmsRegistrationChallenge() {
    }

    public SmsRegistrationChallenge(String phone, Instant now) {
        this.phone = phone;
        windowStartedAt = now;
    }

    public boolean coolingDown(Instant now, Duration cooldown) {
        return lastSentAt != null && lastSentAt.plus(cooldown).isAfter(now);
    }

    public void resetSendWindowIfExpired(Instant now, Duration window) {
        if (!windowStartedAt.plus(window).isAfter(now)) {
            windowStartedAt = now;
            sendCount = 0;
        }
    }

    public int getSendCount() {
        return sendCount;
    }

    public void reserveSend(Instant now) {
        lastSentAt = now;
        sendCount++;
    }

    public void activate(String outId, Instant expiresAt) {
        activate(outId, expiresAt, null);
    }

    public void activate(String outId, Instant expiresAt, Long bindingUserId) {
        this.outId = outId;
        this.expiresAt = expiresAt;
        this.bindingUserId = bindingUserId;
        passwordResetUserId = null;
        loginChallengeId = null;
        attempts = 0;
    }

    public void activatePasswordReset(String outId, Instant expiresAt, Long userId) {
        activate(outId, expiresAt);
        passwordResetUserId = userId;
    }

    public void activateLoginStepUp(String outId, Instant expiresAt, String challengeId) {
        activate(outId, expiresAt);
        loginChallengeId = challengeId;
    }

    public boolean isActive(Instant now) {
        return outId != null && expiresAt.isAfter(now);
    }

    public String getOutId() {
        return outId;
    }

    public Long getBindingUserId() {
        return bindingUserId;
    }

    public Long getPasswordResetUserId() {
        return passwordResetUserId;
    }

    public String getLoginChallengeId() {
        return loginChallengeId;
    }

    public int recordFailedAttempt() {
        return ++attempts;
    }

    public void consume() {
        outId = null;
        expiresAt = null;
        bindingUserId = null;
        passwordResetUserId = null;
        loginChallengeId = null;
    }
}
