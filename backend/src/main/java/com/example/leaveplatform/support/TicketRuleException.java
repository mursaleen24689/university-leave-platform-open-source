package com.example.leaveplatform.support;

/**
 * A ticket that is well formed but cannot go on. The message is written for the
 * person reading the screen, and {@code config/TicketExceptionHandler} turns it
 * into a readable 400.
 */
public class TicketRuleException extends RuntimeException {

    public TicketRuleException(String message) {
        super(message);
    }
}
