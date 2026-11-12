package com.example.leaveplatform.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeaveDayCounterTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

    @Test
    void countsBothDates() {
        assertThat(LeaveDayCounter.countInclusive(MONDAY, MONDAY, Set.of())).isEqualTo(1);
    }

    @Test
    void countsEveryWeekdayWhenThereAreNoHolidays() {
        assertThat(LeaveDayCounter.countInclusive(MONDAY, FRIDAY, Set.of())).isEqualTo(5);
    }

    @Test
    void countsWeekendsTheWayClmsDoes() {
        assertThat(LeaveDayCounter.countInclusive(MONDAY, SUNDAY, Set.of())).isEqualTo(7);
    }

    @Test
    void skipsAPublicHolidayOnAWeekday() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 3));

        assertThat(LeaveDayCounter.countInclusive(MONDAY, FRIDAY, holidays)).isEqualTo(4);
    }

    @Test
    void stillCountsAPublicHolidayThatFallsOnAWeekend() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 7));

        assertThat(LeaveDayCounter.countInclusive(MONDAY, SUNDAY, holidays)).isEqualTo(7);
    }

    @Test
    void returnsZeroForHolidaysOnly() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));

        assertThat(LeaveDayCounter.countInclusive(MONDAY, LocalDate.of(2026, 11, 3), holidays)).isZero();
    }
}
