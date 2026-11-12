package com.example.leaveplatform.support;

/**
 * Where a ticket stands. The four states answer the two questions that matter:
 * does somebody owe an answer, and is the matter finished.
 *
 * <p>{@link #ANSWERED} is the state people get wrong, so it is worth spelling
 * out: it means the desk has replied and the ball is with the staff member. It
 * is not "done". A ticket comes back to the desk the moment that person writes
 * again, which is what keeps a half-answered question visible instead of buried.
 */
public enum TicketStatus {

    /** Written and waiting for the desk. */
    OPEN("Waiting for the desk"),

    /** Somebody at the desk has taken it and it still waits for them. */
    IN_PROGRESS("Being handled"),

    /** The desk has answered; the next word is expected from the staff member. */
    ANSWERED("Answered, waiting for you"),

    /** Finished, either resolved or closed by the desk. */
    CLOSED("Closed");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    /** The words a person reads; the code is what the database keeps. */
    public String label() {
        return label;
    }

    /** Whether the desk still owes this ticket something. */
    public boolean awaitsDesk() {
        return this == OPEN || this == IN_PROGRESS;
    }

    /** Whether the ticket can still be written to. */
    public boolean isOpen() {
        return this != CLOSED;
    }
}
