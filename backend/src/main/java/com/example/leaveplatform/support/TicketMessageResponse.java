package com.example.leaveplatform.support;

import java.time.Instant;

/** One line of the conversation. */
public record TicketMessageResponse(
        Long id,
        String authorName,
        boolean fromDesk,
        String body,
        Instant createdAt) {

    public static TicketMessageResponse of(TicketMessage message) {
        return new TicketMessageResponse(message.getId(), message.getAuthor().getFullName(),
                message.isFromDesk(), message.getBody(), message.getCreatedAt());
    }
}
