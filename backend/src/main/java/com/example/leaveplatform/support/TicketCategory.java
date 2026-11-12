package com.example.leaveplatform.support;

/**
 * What kind of help a ticket asks for. The list is deliberately short and fixed:
 * it exists so the desk can see at a glance which queue a line belongs to, not
 * so it can be reconfigured, and a free-text box would collect a new spelling of
 * the same problem every week.
 */
public enum TicketCategory {

    /** A record to be corrected: a leave balance, a wrong day count, a date. */
    LEAVE_RECORD("Leave or record correction"),

    /** Something to do with the person's own sign-in account. */
    ACCOUNT_ACCESS("Account or sign-in access"),

    /** The platform itself is not doing what it should. */
    SYSTEM_PROBLEM("System problem"),

    /** Anything the three above do not cover. */
    OTHER("Other help");

    private final String label;

    TicketCategory(String label) {
        this.label = label;
    }

    /** The words a person reads; the code is what the database keeps. */
    public String label() {
        return label;
    }
}
