package com.example.leaveplatform.notification;

/**
 * What a message is about. The name is stored in the row and the screen is
 * derived from it here, so a click can only ever land on a page the platform
 * already has - never on an address somebody typed into a column.
 */
public enum NotificationKind {

    /** A request has arrived for this person to recommend or decide. */
    REQUEST_WAITING("approvals"),

    /** The supervisor has recommended a request; the final decision is now due. */
    REQUEST_RECOMMENDED("approvals"),

    /** The applicant pulled a request back before it was decided. */
    REQUEST_WITHDRAWN("approvals"),

    /** The applicant's own request has been approved or refused. */
    REQUEST_DECIDED("leave"),

    /** A help-desk ticket is waiting for somebody at the desk to answer it. */
    TICKET_WAITING("tickets"),

    /** The desk has answered; the next word is expected from the person who asked. */
    TICKET_ANSWERED("tickets"),

    /** The desk finished the ticket. */
    TICKET_CLOSED("tickets"),

    /** A proposed correction to a staff record is waiting for the office to decide. */
    PROFILE_WAITING("admin"),

    /** The office agreed and applied the correction to the record. */
    PROFILE_APPROVED("profile"),

    /** The office refused, and says why in the message. */
    PROFILE_REJECTED("profile"),

    /** A colleague has handed their approving to this person for a window. */
    DELEGATION_OFFERED("delegations"),

    /** A window this person held was closed early, so the approving is back where it started. */
    DELEGATION_REVOKED("delegations"),

    /** This person's own window is closed, held or handed over; the queue moved. */
    DELEGATION_ENDED("delegations"),

    /** A request was recommended or decided under this person's name while they were away. */
    DELEGATION_ACTED("delegations");

    private final String screen;

    NotificationKind(String screen) {
        this.screen = screen;
    }

    /** The route a click on this message opens. */
    public String screen() {
        return screen;
    }
}
