package com.example.leaveplatform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One privileged or authentication action. Rows are written by
 * {@link AuditService} and never changed afterwards; there is deliberately no
 * setter and no relationship to the account, so the entry still makes sense
 * after the account changes.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 60)
    private String actorUsername;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    @Column(name = "target_type", nullable = false, updatable = false, length = 40)
    private String targetType;

    @Column(name = "target_id", updatable = false, length = 60)
    private String targetId;

    @Column(updatable = false, length = 1000)
    private String detail;

    protected AuditEvent() {
    }

    public AuditEvent(String actorUsername, String action, String targetType, String targetId, String detail) {
        this.occurredAt = Instant.now();
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }
}
