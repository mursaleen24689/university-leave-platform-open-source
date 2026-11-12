package com.example.leaveplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.identity.EmployeeRepository;
import com.example.leaveplatform.leave.LeaveBalance;
import com.example.leaveplatform.leave.LeaveBalanceRepository;
import com.example.leaveplatform.leave.LeaveType;
import com.example.leaveplatform.leave.LeaveTypeRepository;

class BalanceAdminServiceTest {

    private static final Long EMPLOYEE_ID = 10L;
    private static final Long TYPE_ID = 5L;
    private static final int YEAR = 2026;

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    private final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final BalanceAdminService service =
            new BalanceAdminService(employees, types, balances, audit);

    private final Employee employee = mock(Employee.class);
    private final LeaveType type = mock(LeaveType.class);

    @BeforeEach
    void configuredEmployeeAndType() {
        when(employee.getId()).thenReturn(EMPLOYEE_ID);
        when(employee.getEmployeeNo()).thenReturn("EMP-001");
        when(employee.getFullName()).thenReturn("Test Employee");
        when(employees.findByIdForUpdate(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(type.getId()).thenReturn(TYPE_ID);
        when(type.getCode()).thenReturn(LeaveType.CASUAL);
        when(type.getName()).thenReturn("Casual Leave");
        when(types.findByCodeAndActiveTrue(LeaveType.CASUAL)).thenReturn(Optional.of(type));
        when(balances.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aCorrectionChangesTheGrantAndLeavesTheDaysUsedAlone() {
        // A mock rather than a real row: use() is deliberately package-private,
        // so only the leave service can spend days, and this test only needs a
        // balance that already holds some.
        LeaveBalance balance = mock(LeaveBalance.class);
        when(balance.getEmployee()).thenReturn(employee);
        when(balance.getLeaveType()).thenReturn(type);
        when(balance.getYear()).thenReturn(YEAR);
        when(balance.getAllocated()).thenReturn(25);
        when(balance.getUsed()).thenReturn(7);
        when(balance.remaining()).thenReturn(18);
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.of(balance));
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        BalanceSummary result = service.adjust(EMPLOYEE_ID, "cl", YEAR, 30, "HR order 12/2026");

        verify(balance).grant(30);
        assertThat(result.used()).isEqualTo(7);
        verify(audit).record(eq(AuditAction.BALANCE_ADJUSTED), eq("EMPLOYEE"), eq(EMPLOYEE_ID),
                ArgumentMatchers.contains("from 25 to 30 day(s)"));
    }

    @Test
    void aMissingBalanceRowIsCreatedWithTheGivenDays() {
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.empty());
        LeaveBalance created = new LeaveBalance(employee, type, YEAR, 18);
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, TYPE_ID, YEAR))
                .thenReturn(Optional.of(created));

        BalanceSummary result = service.adjust(EMPLOYEE_ID, null, YEAR, 18, "Joined in June");

        ArgumentCaptor<LeaveBalance> saved = ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getAllocated()).isEqualTo(18);
        assertThat(result.allocated()).isEqualTo(18);
        assertThat(result.remaining()).isEqualTo(18);
    }

    @Test
    void theGrantCannotGoBelowTheDaysAlreadyUsed() {
        LeaveBalance balance = mock(LeaveBalance.class);
        when(balance.getUsed()).thenReturn(9);
        when(balance.getAllocated()).thenReturn(25);
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", YEAR, 8, "Mistake"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("9 already used");
        verify(balance, never()).grant(anyInt());
    }

    @Test
    void aReasonIsAlwaysNeeded() {
        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", YEAR, 25, "  "))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("needs a reason");
        verify(balances, never()).findForUpdate(anyLong(), anyLong(), anyInt());
    }

    @Test
    void anImpossibleNumberOfDaysIsRefused() {
        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", YEAR, -1, "Typo"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 0 and 365");
        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", YEAR, 400, "Typo"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 0 and 365");
    }

    @Test
    void aYearTheColumnCannotHoldIsRefused() {
        // leave_year is NUMBER(4); without this the office gets an Oracle error
        // instead of a sentence about the box they typed in.
        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", 0, 25, "Typo"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 1900 and 9999");
        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "CL", 10000, 25, "Typo"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 1900 and 9999");
        verify(balances, never()).findForUpdate(anyLong(), anyLong(), anyInt());
    }

    @Test
    void anUnknownPersonIsRefused() {
        when(employees.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjust(99L, "CL", YEAR, 25, "Wrong id"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("no employee record");
    }

    @Test
    void theEmployeeRowIsLockedBeforeABalanceThatDoesNotExistYetIsCreated() {
        when(balances.findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR)).thenReturn(Optional.empty());
        when(balances.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, TYPE_ID, YEAR))
                .thenReturn(Optional.of(new LeaveBalance(employee, type, YEAR, 18)));

        service.adjust(EMPLOYEE_ID, "CL", YEAR, 18, "Joined in June");

        // Without the employee lock two first corrections of the same year would
        // both find no row and both insert one; the unique constraint would then
        // fail the second with an error the office cannot act on.
        InOrder order = inOrder(employees, balances);
        order.verify(employees).findByIdForUpdate(EMPLOYEE_ID);
        order.verify(balances).findForUpdate(EMPLOYEE_ID, TYPE_ID, YEAR);
        order.verify(balances).saveAndFlush(any(LeaveBalance.class));
    }

    @Test
    void anUnknownLeaveTypeIsRefused() {
        when(types.findByCodeAndActiveTrue("MEDICAL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adjust(EMPLOYEE_ID, "medical", YEAR, 25, "Not configured"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("not configured");
    }
}
