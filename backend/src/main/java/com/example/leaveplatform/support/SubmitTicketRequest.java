package com.example.leaveplatform.support;

import jakarta.validation.constraints.Size;

/**
 * A new question for the desk. The category arrives as a code rather than as a
 * free-text word, because the desk routes on it; the detail becomes the first
 * line of the thread, so a ticket with no detail is still a ticket with a
 * subject.
 */
public record SubmitTicketRequest(
        String category,
        @Size(max = SupportTicket.MAX_SUBJECT) String subject,
        @Size(max = TicketMessage.MAX_BODY) String detail) {
}
