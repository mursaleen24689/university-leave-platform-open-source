package com.example.leaveplatform.admin;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.leave.LeaveBalance;
import com.example.leaveplatform.leave.LeaveBalanceRepository;
import com.example.leaveplatform.leave.LeaveType;
import com.example.leaveplatform.leave.LeaveTypeRepository;

/**
 * Corrects a year's allowance. The earlier application let its administrator type a new balance
 * figure on the admin panel, which is what HR needs when a staff member joins
 * mid-year, transfers in with days already spent elsewhere, or has a day
 * returned after a mistaken approval.
 *
 * <p>Only {@code allocated} is touched, never {@code used}, so the remaining
 * figure stays the honest difference between the two. Every adjustment needs a
 * reason and is audited with the old and the new number.
 */
@Service
public class BalanceAdminService {

    /** A year cannot hold more leave days than it has days; above this the figure is a slip. */
    private static final int MAX_ALLOWED = 365;

    private final EmployeeRepository employees;
    private final LeaveTypeRepository types;
    private final LeaveBalanceRepository balances;
    private final AuditService audit;

    public BalanceAdminService(EmployeeRepository employees, LeaveTypeRepository types,
            LeaveBalanceRepository balances, AuditService audit) {
        this.employees = employees;
        this.types = types;
        this.balances = balances;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BalanceSummary> forEmployee(Long employeeId) {
        return balances.findByEmployeeIdOrderByYearDesc(employeeId).stream()
                .map(BalanceAdminService::toSummary)
                .toList();
    }

    @Transactional
    public BalanceSummary adjust(Long employeeId, String leaveTypeCode, int year, int allocated,
            String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new AdminException("A balance correction needs a reason, such as the HR order number.");
        }
        if (allocated < 0 || allocated > MAX_ALLOWED) {
            throw new AdminException("The days granted must be between 0 and " + MAX_ALLOWED + ".");
        }
        // leave_year is NUMBER(4), and a mistyped box is a bad request rather
        // than an Oracle error the office cannot read.
        if (year < 1900 || year > 9999) {
            throw new AdminException("Enter a year between 1900 and 9999.");
        }
        // The employee row, not just the balance row: on the first correction of
        // a year there is no balance to lock, and this is the same row the
        // approval path locks before it creates one, so a correction and a
        // decision at the same moment queue instead of both inserting.
        Employee employee = employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new AdminException("There is no employee record with that number."));
        LeaveType type = types.findByCodeAndActiveTrue(blankToDefault(leaveTypeCode, LeaveType.CASUAL))
                .orElseThrow(() -> new AdminException("That leave type is not configured."));

        LeaveBalance balance = balances.findForUpdate(employeeId, type.getId(), year)
                .orElse(null);
        if (balance == null) {
            balances.saveAndFlush(new LeaveBalance(employee, type, year, allocated));
            audit.record(AuditAction.BALANCE_ADJUSTED, "EMPLOYEE", employeeId,
                    "Created the " + type.getCode() + " " + year + " balance of "
                            + employee.getEmployeeNo() + " " + employee.getFullName()
                            + " with " + allocated + " day(s). Reason: " + reason.trim() + ".");
        } else {
            if (allocated < balance.getUsed()) {
                throw new AdminException("Cannot set %d day(s): %d already used in %s %d."
                        .formatted(allocated, balance.getUsed(), type.getCode(), year));
            }
            int before = balance.getAllocated();
            balance.grant(allocated);
            audit.record(AuditAction.BALANCE_ADJUSTED, "EMPLOYEE", employeeId,
                    "Changed the " + type.getCode() + " " + year + " allowance of "
                            + employee.getEmployeeNo() + " " + employee.getFullName() + " from "
                            + before + " to " + allocated + " day(s). Reason: " + reason.trim() + ".");
        }
        return toSummary(balances.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year)
                .orElseThrow());
    }

    private static String blankToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
    }

    private static BalanceSummary toSummary(LeaveBalance balance) {
        Employee employee = balance.getEmployee();
        LeaveType type = balance.getLeaveType();
        return new BalanceSummary(balance.getId(), employee.getEmployeeNo(), employee.getFullName(),
                type.getCode(), type.getName(), balance.getYear(), balance.getAllocated(),
                balance.getUsed(), balance.remaining());
    }
}
