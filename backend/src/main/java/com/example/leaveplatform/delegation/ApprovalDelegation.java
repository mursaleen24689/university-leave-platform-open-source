package com.example.leaveplatform.delegation;

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
import java.time.LocalDate;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

/**
 * One person handing one approving step to a named colleague for a dated window.
 *
 * <p>Two things about this row are easy to get wrong and are worth saying where
 * the code is.
 *
 * <ul>
 *   <li>The delegatee acts as <b>themself</b>. Every decision made under a
 *       delegation names the person who pressed the button, and the audit detail
 *       adds that they were acting for the delegator. This is the whole point of
 *       the feature: the alternative, which the earlier system lived on, was somebody with the
 *       admin password signing in as the absent officer, after which the record
 *       named a person who was on leave and had read none of it.
 *   <li>An ended window is not a special state. {@code active} says whether the
 *       delegator closed it early; whether it is usable is {@code active} plus
 *       today lying between the two dates. Nothing has to run at midnight for a
 *       delegation to expire, and no forgotten row can expire back into life.
 * </ul>
 *
 * <p>The department on a DECISION delegation is a snapshot taken when the window
 * was written, not a live join: the authority handed over was over that
 * department's decisions, and a later transfer of either person must not move it
 * somewhere nobody agreed to.
 *
 * <p>The reference is written by the database, exactly as for tickets and profile
 * requests - see {@code docs/DECISIONS.md} ADR-016 - so the mapping reads the
 * column and never writes it, and {@link #stampReference} is only a display copy.
 */
@Entity
@Table(name = "approval_delegation")
public class ApprovalDelegation {

    /** The column sizes, public so a screen can match them instead of guessing. */
    public static final int MAX_REASON = 1000;
    static final int MAX_REFERENCE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, length = 20,
            insertable = false, updatable = false)
    private String reference;

    /** Whose approving this is. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_employee_id", nullable = false)
    private Employee fromEmployee;

    /** Who may act during the window - never the same person. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_employee_id", nullable = false)
    private Employee toEmployee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DelegationScope scope;

    /** Only for DECISION: the department whose decisions are covered. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_employee_id")
    private Employee revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalDelegation() {
    }

    public ApprovalDelegation(Employee from, Employee to, DelegationScope scope,
            Department department, LocalDate startDate, LocalDate endDate, String reason) {
        this.fromEmployee = from;
        this.toEmployee = to;
        this.scope = scope;
        this.department = department;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.active = true;
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
     * the screen that just accepted a delegation can quote it without reading the
     * row again. The trigger is the only writer. Public so the leave module's
     * tests can build a window with the number its messages will quote.
     */
    public void stampReference() {
        if (reference == null && id != null) {
            reference = "DL-" + String.format("%06d", id);
        }
    }

    /**
     * Snapshots the department a final-decision window covers: the department
     * the delegator headed when the window was written. The constructor leaves it
     * unset because only the service knows, from the signed-in account's roles,
     * that this row is of that kind at all.
     */
    void coverDepartment(Department department) {
        this.department = department;
    }

    /** The delegator closes the window early; the row stays as the record it was. */
    void revoke(Employee who) {
        this.active = false;
        this.revokedBy = who;
        this.revokedAt = Instant.now();
    }

    /** Whether today this row can be used at all - dates included, not just the flag. */
    public boolean covers(LocalDate day) {
        return active && !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    /**
     * Whether this row can be used for a request that is waiting <b>today</b>. The
     * platform's queues are who-is-waiting-now lists, so the window has to be open
     * now; a delegation written for next month must not empty a queue early, and
     * one that ended must not keep accepting.
     */
    public boolean live() {
        return covers(LocalDate.now());
    }

    /** How a screen describes where the window stands today. */
    public String state(LocalDate today) {
        if (!active) {
            return "Revoked";
        }
        if (today.isBefore(startDate)) {
            return "Starts " + startDate;
        }
        if (today.isAfter(endDate)) {
            return "Ended " + endDate;
        }
        return today.equals(endDate) ? "Live, last day is today" : "Live";
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Employee getFromEmployee() {
        return fromEmployee;
    }

    public Employee getToEmployee() {
        return toEmployee;
    }

    public DelegationScope getScope() {
        return scope;
    }

    public Department getDepartment() {
        return department;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public boolean isActive() {
        return active;
    }

    public Employee getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
