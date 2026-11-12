package com.example.leaveplatform.profile;

/**
 * Where a proposed correction stands. Three of the four states are finished and
 * only ever reached once: what happens to a staff record is a decision, and a
 * decision that can be revisited is not a decision.
 */
public enum ProfileRequestStatus {

    /** Written and waiting for the office. */
    PENDING("Waiting for the office"),

    /** The office agreed and the record now says what was asked. */
    APPROVED("Approved"),

    /** The office refused, with a reason. */
    REJECTED("Refused"),

    /** The person who asked took it back before it was decided. */
    CANCELLED("Withdrawn");

    private final String label;

    ProfileRequestStatus(String label) {
        this.label = label;
    }

    /** The words a person reads; the code is what the database keeps. */
    public String label() {
        return label;
    }

    /** Whether the office still owes this request an answer. */
    public boolean isPending() {
        return this == PENDING;
    }
}
