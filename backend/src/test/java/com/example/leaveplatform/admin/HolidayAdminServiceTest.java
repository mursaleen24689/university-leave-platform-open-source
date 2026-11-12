package com.example.leaveplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.leaveplatform.audit.AuditAction;
import com.example.leaveplatform.audit.AuditService;
import com.example.leaveplatform.leave.PublicHoliday;
import com.example.leaveplatform.leave.PublicHolidayRepository;

class HolidayAdminServiceTest {

    private static final LocalDate EID = LocalDate.of(2026, 12, 4);

    private final PublicHolidayRepository holidays = mock(PublicHolidayRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final HolidayAdminService service = new HolidayAdminService(holidays, audit);

    @BeforeEach
    void saveReturnsTheRow() {
        when(holidays.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void aNewHolidayIsListedAndAudited() {
        HolidaySummary created = service.add(EID, "  Eid-ul-Fitr (2nd day)  ");

        ArgumentCaptor<PublicHoliday> saved = ArgumentCaptor.forClass(PublicHoliday.class);
        verify(holidays).save(saved.capture());
        assertThat(created.date()).isEqualTo(EID);
        assertThat(saved.getValue().getName()).isEqualTo("Eid-ul-Fitr (2nd day)");
        verify(audit).record(eq(AuditAction.HOLIDAY_ADDED), eq("PUBLIC_HOLIDAY"), any(), any());
    }

    @Test
    void aMistypedYearIsABadRequestRatherThanAServerError() {
        // LocalDate.of throws below year 1, which would reach the office as a
        // generic 500 instead of a sentence about the box they typed in.
        assertThatThrownBy(() -> service.list(0))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 1900 and 9999");
        assertThatThrownBy(() -> service.list(10000))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("between 1900 and 9999");
        verify(holidays, never()).findByDateIsBetweenOrderByDateAsc(any(), any());
    }

    @Test
    void aDateThatIsAlreadyInTheCalendarIsRefused() {
        when(holidays.existsByDate(EID)).thenReturn(true);

        assertThatThrownBy(() -> service.add(EID, "Birthday of the founder"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("already a holiday on 2026-12-04");
        verify(holidays, never()).save(any());
    }

    @Test
    void aHolidayNeedsADateAndAName() {
        assertThatThrownBy(() -> service.add(null, "Eid"))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("Choose the date");
        assertThatThrownBy(() -> service.add(EID, "   "))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("Give the holiday a name");
    }

    @Test
    void aVeryLongNameIsRefused() {
        assertThatThrownBy(() -> service.add(EID, "x".repeat(151)))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("150 characters");
    }

    @Test
    void removingAHolidayIsAudited() {
        PublicHoliday holiday = new PublicHoliday(EID, "Eid-ul-Fitr (2nd day)");
        when(holidays.findById(3L)).thenReturn(Optional.of(holiday));

        service.remove(3L);

        verify(holidays).delete(holiday);
        verify(audit).record(eq(AuditAction.HOLIDAY_REMOVED), eq("PUBLIC_HOLIDAY"), eq(3L), any());
    }

    @Test
    void removingAHolidayThatWentAwayIsRefused() {
        when(holidays.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(3L))
                .isInstanceOf(AdminException.class)
                .hasMessageContaining("no longer in the calendar");
        verify(holidays, never()).delete(any());
    }

    @Test
    void theListIsForTheChosenYearOnly() {
        when(holidays.findByDateIsBetweenOrderByDateAsc(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(new PublicHoliday(EID, "Eid-ul-Fitr (2nd day)")));

        List<HolidaySummary> found = service.list(2026);

        assertThat(found).singleElement()
                .satisfies(row -> assertThat(row.name()).isEqualTo("Eid-ul-Fitr (2nd day)"));
    }
}
