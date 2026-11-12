package com.example.leaveplatform.support;

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
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

/**
 * One help-desk question. The row carries only the state - what it is about, who
 * asked, who is handling it and whether it is finished - while the words live in
 * {@link TicketMessage}, which is never edited.
 *
 * <p>The department is stored rather than read through the employee every time,
 * so a transfer next year does not move this ticket into the new department's
 * queue or change what an old report says.
 */
@Entity
@Table(name = "support_ticket")
public class SupportTicket {

    /** The column sizes, public so a screen can match them instead of guessing. */
    public static final int MAX_SUBJECT = 200;
    static final int MAX_REFERENCE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The number both sides quote, derived from the id so it cannot collide.
     * Only the database can know the id at the moment the row is written, so
     * the column is written by the trigger in V014 and this mapping reads it and
     * never writes it; {@link #stampReference} puts the same value into memory so
     * a brand new ticket can be shown with its number without a second read.
     */
    @Column(name = "reference", nullable = false, length = 20,
            insertable = false, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by_employee_id", nullable = false)
    private Employee openedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketCategory category;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_employee_id")
    private Employee assignee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected SupportTicket() {
    }

    public SupportTicket(Employee openedBy, Department department, TicketCategory category,
            String subject) {
        this.openedBy = openedBy;
        this.department = department;
        this.category = category;
        this.subject = subject;
        this.status = TicketStatus.OPEN;
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
     * Mirrors, in memory, the number the insert trigger writes into the column,
     * so the screen that just accepted a ticket can quote it back without reading
     * the row again. The column itself is read-only in this mapping and the
     * trigger is the only writer, so nothing here can change a stored number.
     */
    void stampReference() {
        if (reference == null && id != null) {
            reference = "HD-" + String.format("%06d", id);
        }
    }

    /** The desk takes it. A ticket already assigned simply changes hands. */
    void assign(Employee assignee) {
        this.assignee = assignee;
        this.status = TicketStatus.IN_PROGRESS;
    }

    /** The desk has written a line: the next word is expected from the staff member. */
    void answeredByDesk() {
        this.status = TicketStatus.ANSWERED;
    }

    /**
     * The staff member has written again, so the desk owes an answer. Whether the
     * ticket lands back with a named person or in the unassigned queue depends
     * only on whether somebody had already taken it.
     */
    void answeredByStaff() {
        this.status = assignee == null ? TicketStatus.OPEN : TicketStatus.IN_PROGRESS;
        this.closedAt = null;
    }

    void close() {
        this.status = TicketStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    /** The problem came back, so the ticket is put where an answer is due. */
    void reopen() {
        this.status = assignee == null ? TicketStatus.OPEN : TicketStatus.IN_PROGRESS;
        this.closedAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Employee getOpenedBy() {
        return openedBy;
    }

    public Department getDepartment() {
        return department;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Employee getAssignee() {
        return assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
