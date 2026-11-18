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
        consume();
    }

    public void activate(String outId, Instant expiresAt) {
        this.outId = outId;
        this.expiresAt = expiresAt;
        attempts = 0;
    }

    public boolean isActive(Instant now) {
        return outId != null && expiresAt.isAfter(now);
    }

    public String getOutId() {
        return outId;
    }

    public int recordFailedAttempt() {
        return ++attempts;
    }

    public void consume() {
        outId = null;
        expiresAt = null;
    }
}
