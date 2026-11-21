package neko.fantasy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_logs_actor_id", columnList = "actor_id")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_username", nullable = false, length = 32)
    private String actorUsername;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_username", length = 32)
    private String targetUsername;

    @Column(name = "details", nullable = false, length = 2000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long actorId, String actorUsername, String action, Long targetId,
                    String targetUsername, String details, String ipAddress) {
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetId = targetId;
        this.targetUsername = targetUsername;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public Long getTargetId() { return targetId; }
    public String getTargetUsername() { return targetUsername; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
