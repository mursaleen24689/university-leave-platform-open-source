package com.example.leaveplatform.support;

import jakarta.validation.constraints.Size;

/** A line added to an existing conversation, by either side. */
public record TicketReplyRequest(
        @Size(max = TicketMessage.MAX_BODY) String body) {
}
