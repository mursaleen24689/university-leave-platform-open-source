package com.example.leaveplatform.leave;

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

@Entity
@Table(name = "leave_application")
public class LeaveApplication {

    /** The prefix the printed form and the verification read both quote. */
    public static final String REFERENCE_PREFIX = "LV-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Written by the database from the id, as for a ticket, a record correction
     * and a delegated window - {@code docs/DECISIONS.md} ADR-016 - so the
     * mapping reads the column and never writes it, and {@link #stampReference}
     * is only a display copy for the row that was just saved.
     */
    @Column(name = "reference", nullable = false, length = 20,
            insertable = false, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int days;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status;

    @Column(name = "supervisor_employee_id")
    private Long supervisorEmployeeId;

    @Column(name = "supervisor_decided_at")
    private Instant supervisorDecidedAt;

    @Column(name = "decided_by_employee_id")
    private Long decidedByEmployeeId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(nullable = false)
    private boolean compensated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveApplication() {
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

    LeaveApplication(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
            int days, String reason, LeaveStatus status, Long supervisorEmployeeId) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
        this.status = status;
        this.supervisorEmployeeId = supervisorEmployeeId;
    }

    void recommend(String remarks) {
        this.status = LeaveStatus.PENDING;
        this.supervisorDecidedAt = Instant.now();
        this.remarks = remarks;
    }

    void rejectBySupervisor(String remarks) {
        this.status = LeaveStatus.REJECTED;
        this.supervisorDecidedAt = Instant.now();
        this.remarks = remarks;
    }

    void decide(boolean approve, boolean compensate, Long decidedBy, String remarks) {
        this.status = approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
        this.compensated = approve && compensate;
        this.decidedByEmployeeId = decidedBy;
        this.decidedAt = Instant.now();
        this.remarks = remarks;
    }

    /**
     * The applicant cancels a request that no one has decided. It is kept as a
     * WITHDRAWN row rather than deleted so the history stays complete; no
     * balance is touched because days are only spent on approval.
     */
    void withdraw(String note) {
        this.status = LeaveStatus.WITHDRAWN;
        this.withdrawnAt = Instant.now();
        this.remarks = note;
    }

    public Long getId() {
        return id;
    }

    /**
     * Mirrors, in memory, the number the insert trigger writes into the column,
     * so the screen that just accepted a request can quote it without reading
     * the row back. The trigger is the only writer.
     */
    public void stampReference() {
        if (reference == null && id != null) {
            reference = REFERENCE_PREFIX + String.format("%06d", id);
        }
    }

    public String getReference() {
        return reference;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public Long getSupervisorEmployeeId() {
        return supervisorEmployeeId;
    }

    public Instant getSupervisorDecidedAt() {
        return supervisorDecidedAt;
    }

    public Long getDecidedByEmployeeId() {
        return decidedByEmployeeId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public boolean isCompensated() {
        return compensated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
