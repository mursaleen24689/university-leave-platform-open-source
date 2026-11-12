package com.example.leaveplatform.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import com.example.leaveplatform.identity.Employee;

/**
 * Days granted and used for one employee, leave type and year. Remaining is
 * derived so the two numbers can never disagree.
 */
@Entity
@Table(name = "leave_balance")
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "leave_year", nullable = false)
    private int year;

    @Column(nullable = false)
    private int allocated;

    @Column(nullable = false)
    private int used;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected LeaveBalance() {
    }

    public LeaveBalance(Employee employee, LeaveType leaveType, int year, int allocated) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.year = year;
        this.allocated = allocated;
        this.used = 0;
    }

    public int remaining() {
        return allocated - used;
    }

    boolean canCover(int days) {
        return remaining() >= days;
    }

    void use(int days) {
        used += days;
    }

    /**
     * Sets the days granted for the year. Only the allowance created on first
     * use and an administrator's audited correction should call it. The caller
     * must keep the rule that the days already used cannot be exceeded; the
     * database CHECK constraint refuses the write otherwise.
     */
    public void grant(int days) {
        this.allocated = days;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public int getYear() {
        return year;
    }

    public int getAllocated() {
        return allocated;
    }

    public int getUsed() {
        return used;
    }
}
