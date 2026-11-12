package com.example.leaveplatform.audit;

/**
 * Action codes stored in {@code audit_event.action}. Constants rather than free
 * text, so a report can count them and a typo cannot create a second spelling.
 */
public final class AuditAction {

    public static final String FACULTY_CREATED = "FACULTY_CREATED";
    public static final String FACULTY_ACTIVATED = "FACULTY_ACTIVATED";
    public static final String FACULTY_DEACTIVATED = "FACULTY_DEACTIVATED";

    public static final String DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    public static final String DEPARTMENT_RENAMED = "DEPARTMENT_RENAMED";
    public static final String DEPARTMENT_ACTIVATED = "DEPARTMENT_ACTIVATED";
    public static final String DEPARTMENT_DEACTIVATED = "DEPARTMENT_DEACTIVATED";

    public static final String EMPLOYEE_CREATED = "EMPLOYEE_CREATED";
    public static final String EMPLOYEE_UPDATED = "EMPLOYEE_UPDATED";
    public static final String EMPLOYEE_ACTIVATED = "EMPLOYEE_ACTIVATED";
    public static final String EMPLOYEE_DEACTIVATED = "EMPLOYEE_DEACTIVATED";
    public static final String EMPLOYEE_IMPORTED = "EMPLOYEE_IMPORTED";

    public static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    public static final String ACCOUNT_ACTIVATED = "ACCOUNT_ACTIVATED";
    public static final String ACCOUNT_DEACTIVATED = "ACCOUNT_DEACTIVATED";
    public static final String ROLES_REPLACED = "ROLES_REPLACED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    public static final String SIGNED_IN = "SIGNED_IN";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    /**
     * The second step of signing in, one row each for the three ways it changes:
     * the person turned it on, the person turned it off, and the administration
     * team turned it off for somebody who cannot sign in any more.
     */
    public static final String TWO_STEP_ENABLED = "TWO_STEP_ENABLED";
    public static final String TWO_STEP_DISABLED = "TWO_STEP_DISABLED";
    public static final String TWO_STEP_CLEARED = "TWO_STEP_CLEARED";

    public static final String LEAVE_SUBMITTED = "LEAVE_SUBMITTED";
    public static final String LEAVE_WITHDRAWN = "LEAVE_WITHDRAWN";
    public static final String LEAVE_RECOMMENDED = "LEAVE_RECOMMENDED";
    public static final String LEAVE_REJECTED = "LEAVE_REJECTED";
    public static final String LEAVE_APPROVED = "LEAVE_APPROVED";

    public static final String HOLIDAY_ADDED = "HOLIDAY_ADDED";
    public static final String HOLIDAY_REMOVED = "HOLIDAY_REMOVED";
    public static final String BALANCE_ADJUSTED = "BALANCE_ADJUSTED";

    public static final String REPORT_EXPORTED = "REPORT_EXPORTED";

    /**
     * Paper leaving the platform. A printed form carries the university's name
     * and a code that looks official, so the trail has to say who produced it
     * and which requests went onto it.
     */
    public static final String LEAVE_FORM_PRINTED = "LEAVE_FORM_PRINTED";

    /**
     * A person's year on paper. The sheet is the kind an office signs and files,
     * so the trail says whose records went out and for which year.
     */
    public static final String LEAVE_RECORD_PRINTED = "LEAVE_RECORD_PRINTED";

    public static final String MAIL_SETTINGS_UPDATED = "MAIL_SETTINGS_UPDATED";
    public static final String MAIL_TEST_SENT = "MAIL_TEST_SENT";

    public static final String TICKET_ASSIGNED = "TICKET_ASSIGNED";
    public static final String TICKET_ANSWERED = "TICKET_ANSWERED";
    public static final String TICKET_CLOSED = "TICKET_CLOSED";
    public static final String TICKET_REOPENED = "TICKET_REOPENED";

    public static final String PROFILE_REQUEST_APPROVED = "PROFILE_REQUEST_APPROVED";
    public static final String PROFILE_REQUEST_REJECTED = "PROFILE_REQUEST_REJECTED";

    public static final String DELEGATION_CREATED = "DELEGATION_CREATED";
    public static final String DELEGATION_REVOKED = "DELEGATION_REVOKED";

    private AuditAction() {
    }
}
