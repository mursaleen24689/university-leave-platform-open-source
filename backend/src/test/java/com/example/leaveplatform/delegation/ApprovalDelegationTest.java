package com.example.leaveplatform.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.organization.Department;

/**
 * The two questions a window is always asked: can it be used on this day, and
 * what does a screen call it today. A window that closed by its own dates and
 * one that was revoked both answer "no" to the first, but only the second ever
 * mentions a person who closed it - the difference the platform must not lose.
 */
class ApprovalDelegationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate NEXT_WEDNESDAY = LocalDate.of(2026, 11, 11);

    private final Employee from = mock(Employee.class);
    private final Employee to = mock(Employee.class);
    private final Employee office = mock(Employee.class);
    private final Department department = mock(Department.class);

    private ApprovalDelegation window(LocalDate start, LocalDate end) {
        return new ApprovalDelegation(from, to, DelegationScope.DECISION, department, start, end, null);
    }

    @Test
    void theWindowCoversEveryDayFromItsFirstToItsLast() {
        ApprovalDelegation delegation = window(MONDAY, FRIDAY);

        assertThat(delegation.covers(MONDAY.minusDays(1))).isFalse();
        assertThat(delegation.covers(MONDAY)).isTrue();
        assertThat(delegation.covers(FRIDAY)).isTrue();
        assertThat(delegation.covers(FRIDAY.plusDays(1))).isFalse();
    }

    @Test
    void aRevokedWindowCoversNothingFromTheDayItClosed() {
        ApprovalDelegation delegation = window(MONDAY, FRIDAY);
        delegation.revoke(office);

        assertThat(delegation.covers(MONDAY)).isFalse();
        assertThat(delegation.covers(FRIDAY)).isFalse();
        assertThat(delegation.isActive()).isFalse();
        assertThat(delegation.getRevokedBy()).isSameAs(office);
        assertThat(delegation.getRevokedAt()).isNotNull();
    }

    @Test
    void aScreenDescribesWhereTheWindowStandsToday() {
        assertThat(window(NEXT_WEDNESDAY, NEXT_WEDNESDAY.plusDays(4)).state(MONDAY))
                .isEqualTo("Starts 2026-11-11");
        assertThat(window(MONDAY, FRIDAY).state(MONDAY.plusDays(1))).isEqualTo("Live");
        assertThat(window(MONDAY, FRIDAY).state(FRIDAY)).isEqualTo("Live, last day is today");
        assertThat(window(MONDAY, FRIDAY).state(FRIDAY.plusDays(1))).isEqualTo("Ended 2026-11-06");

        ApprovalDelegation closed = window(MONDAY, FRIDAY);
        closed.revoke(office);
        assertThat(closed.state(MONDAY)).isEqualTo("Revoked");
    }

    @Test
    void theReferenceComesFromTheIdAndNeverFromAType() {
        ApprovalDelegation delegation = window(MONDAY, FRIDAY);
        setId(delegation, 42L);
        delegation.stampReference();

        assertThat(delegation.getReference()).isEqualTo("DL-000042");
        // The display copy is a mirror of what the trigger writes; asking twice
        // must not invent a second number.
        delegation.stampReference();
        assertThat(delegation.getReference()).isEqualTo("DL-000042");
    }

    private void setId(ApprovalDelegation delegation, long id) {
        try {
            var field = ApprovalDelegation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(delegation, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
