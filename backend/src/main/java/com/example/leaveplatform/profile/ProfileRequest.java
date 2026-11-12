package com.example.leaveplatform.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import com.example.leaveplatform.identity.Employee;

/**
 * One set of corrections one member of staff proposes for their own record, and
 * the office's answer to them.
 *
 * <p>The row carries a value per editable field, which means a blank column says
 * nothing on its own: Oracle stores an empty string as NULL, so "no phone number"
 * and "I was not asked about the phone" look identical. {@code changed_fields} is
 * what settles it, and {@link #changes()} is the only way the rest of the
 * platform reads the row - it returns pairs for the named fields and nothing for
 * the others, so an unlisted value cannot reach a staff record by accident. That
 * is the whole of the rule the owner asked to keep: an earlier system approved a profile
 * change by writing every column it was handed, and the columns nobody mentioned
 * went NULL.
 *
 * <p>The reference is written by the database, exactly as a help-desk ticket
 * number is - see {@code docs/DECISIONS.md} ADR-016 - so the mapping reads the
 * column and never writes it, and {@link #stampReference} is only a display copy.
 */
@Entity
@Table(name = "profile_request")
public class ProfileRequest {

    /** The column sizes, public so a screen can match them instead of guessing. */
    public static final int MAX_REASON = 1000;
    public static final int MAX_NOTE = 1000;
    static final int MAX_REFERENCE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 20,
            insertable = false, updatable = false)
    private String reference;

    /** Whose record this is, and the person who asked - they are the same. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "changed_fields", nullable = false, length = 200)
    private String changedFields;

    @Column(name = "full_name_proposed", length = 150)
    private String fullNameProposed;

    @Column(name = "employee_no_proposed", length = 30)
    private String employeeNoProposed;

    @Column(name = "designation_proposed", length = 100)
    private String designationProposed;

    @Column(name = "email_proposed", length = 150)
    private String emailProposed;

    @Column(name = "phone_proposed", length = 40)
    private String phoneProposed;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_employee_id")
    private Employee decidedBy;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected ProfileRequest() {
    }

    public ProfileRequest(Employee employee, String reason) {
        this.employee = employee;
        this.reason = reason;
        this.changedFields = "";
        this.status = ProfileRequestStatus.PENDING;
    }

    @PrePersist
    void stampCreation() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void stampUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Mirrors, in memory, the number the insert trigger writes into the column, so
     * the screen that just accepted a request can quote it without reading the row
     * again. The trigger is the only writer.
     */
    void stampReference() {
        if (reference == null && id != null) {
            reference = "PR-" + String.format("%06d", id);
        }
    }

    /** Asks this request to change one field to this value. */
    void propose(ProfileField field, String value) {
        switch (field) {
            case FULL_NAME -> fullNameProposed = value;
            case EMPLOYEE_NO -> employeeNoProposed = value;
            case DESIGNATION -> designationProposed = value;
            case EMAIL -> emailProposed = value;
            case PHONE -> phoneProposed = value;
        }
        EnumSet<ProfileField> named = changedFields();
        named.add(field);
        changedFields = ProfileField.join(named);
    }

    /** The fields this request changes, in the order a screen lists them. */
    public EnumSet<ProfileField> changedFields() {
        return ProfileField.parse(changedFields);
    }

    /**
     * What this request asks for, as field-and-value pairs. Only named fields
     * appear, so whatever else the row holds in its columns is not offered to
     * whoever applies it.
     */
    public List<ProposedChange> changes() {
        List<ProposedChange> changes = new ArrayList<>();
        for (ProfileField field : changedFields()) {
            changes.add(new ProposedChange(field, proposedValue(field)));
        }
        return List.copyOf(changes);
    }

    private String proposedValue(ProfileField field) {
        return switch (field) {
            case FULL_NAME -> fullNameProposed;
            case EMPLOYEE_NO -> employeeNoProposed;
            case DESIGNATION -> designationProposed;
            case EMAIL -> emailProposed;
            case PHONE -> phoneProposed;
        };
    }

    /** One field and the value asked for it. */
    public record ProposedChange(ProfileField field, String value) {
    }

    /** The office agreed: the record is about to say what was asked. */
    void approve(Employee officer, String note) {
        decide(officer, note, ProfileRequestStatus.APPROVED);
    }

    /** The office refused, with a reason the person can act on. */
    void reject(Employee officer, String note) {
        decide(officer, note, ProfileRequestStatus.REJECTED);
    }

    /** The person who asked took it back before it was decided. */
    void cancel(Employee whoAsked) {
        decide(whoAsked, null, ProfileRequestStatus.CANCELLED);
    }

    private void decide(Employee who, String note, ProfileRequestStatus outcome) {
        this.status = outcome;
        this.decidedBy = who;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getReason() {
        return reason;
    }

    public ProfileRequestStatus getStatus() {
        return status;
    }

    public Employee getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
