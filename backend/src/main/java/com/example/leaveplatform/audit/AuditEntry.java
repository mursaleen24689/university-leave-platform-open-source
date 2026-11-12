package com.example.leaveplatform.audit;

import java.time.Instant;

/** A row of the audit trail as the screen and the API present it. */
public record AuditEntry(Long id, Instant occurredAt, String actorUsername, String action,
        String targetType, String targetId, String detail) {

    static AuditEntry of(AuditEvent event) {
        return new AuditEntry(event.getId(), event.getOccurredAt(), event.getActorUsername(),
                event.getAction(), event.getTargetType(), event.getTargetId(), event.getDetail());
    }
}
