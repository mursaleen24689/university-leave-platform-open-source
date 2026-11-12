package com.example.leaveplatform.support;

/**
 * Who at the desk takes the ticket. It names a sign-in account rather than a
 * typed name, so the person recorded as the assignee is one that can actually
 * open the ticket and answer it - the same reason a notification is addressed to
 * an account and not to free text.
 */
public record TicketAssignRequest(String username) {
}
