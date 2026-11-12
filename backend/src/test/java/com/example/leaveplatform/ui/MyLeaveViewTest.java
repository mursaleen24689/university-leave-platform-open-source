package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The apply screen refuses a last day that falls before the first day, which is
 * the same rule {@code LeaveService} enforces arriving as a sentence to read
 * out. The picker itself cannot be built in a unit test - the shared header
 * holds {@code RouterLink}s and those need a live Vaadin router - so the rule
 * the listener applies is the part under test here.
 */
class MyLeaveViewTest {

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2026, month, dayOfMonth);
    }

    @Test
    void aLastDayBeforeTheFirstDayIsLeftBehind() {
        assertThat(MyLeaveView.leftBehind(day(12, 28), day(12, 20))).isTrue();
    }

    @Test
    void aLastDayOnOrAfterTheFirstDaySurvives() {
        assertThat(MyLeaveView.leftBehind(day(12, 28), day(12, 28))).isFalse();
        assertThat(MyLeaveView.leftBehind(day(12, 28), day(12, 31))).isFalse();
    }

    @Test
    void aSingleDayRequestHasNoLastDayToLeaveBehind() {
        assertThat(MyLeaveView.leftBehind(day(12, 28), null)).isFalse();
    }

    @Test
    void aFirstDayNotYetChosenLeavesTheLastDayFree() {
        assertThat(MyLeaveView.leftBehind(null, day(12, 20))).isFalse();
    }

    @Test
    void aLastDayInTheOldYearIsLeftBehindWhenTheFirstDayMovesIntoTheNewYear() {
        assertThat(MyLeaveView.leftBehind(LocalDate.of(2027, 1, 4), day(12, 30))).isTrue();
    }
}
