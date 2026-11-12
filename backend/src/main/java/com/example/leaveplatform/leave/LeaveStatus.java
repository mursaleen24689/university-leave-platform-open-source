package com.example.leaveplatform.leave;

/**
 * A request moves from the supervisor step (only when the employee has one) to
 * the HOD's decision and then to a final state. The applicant can pull a request
 * back while it is still in one of the two waiting states.
 */
public enum LeaveStatus {

    PENDING_SUPERVISOR("Waiting for supervisor"),
    PENDING("Waiting for HOD"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn by applicant");

    private final String label;

    LeaveStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isOpen() {
        return this == PENDING_SUPERVISOR || this == PENDING;
    }
}
