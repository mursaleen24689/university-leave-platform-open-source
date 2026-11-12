package com.example.leaveplatform.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The wording here is what a person reads above a filtered list, so it is worth
 * testing: a screen that says "showing everything" while it hid half the year
 * is worse than no filter at all.
 */
class LeaveFilterTest {

    private static final LocalDate FIRST = LocalDate.of(2026, 11, 2);
    private static final LocalDate LAST = LocalDate.of(2026, 11, 4);

    @Test
    void noChoiceAtAllMeansEverything() {
        LeaveFilter filter = LeaveFilter.any();

        assertThat(filter.isAny()).isTrue();
        assertThat(filter.describe()).isEqualTo("everything");
    }

    @Test
    void anyOnePartIsAlreadyAFilter() {
        assertThat(new LeaveFilter(FIRST, null, null).isAny()).isFalse();
        assertThat(new LeaveFilter(null, LAST, null).isAny()).isFalse();
        assertThat(new LeaveFilter(null, null, LeaveStatus.APPROVED).isAny()).isFalse();
    }

    @Test
    void eachKindOfPeriodIsNamedInItsOwnWords() {
        assertThat(new LeaveFilter(FIRST, LAST, null).describe())
                .isEqualTo("from 2026-11-02 to 2026-11-04");
        assertThat(new LeaveFilter(FIRST, null, null).describe()).isEqualTo("from 2026-11-02 onwards");
        assertThat(new LeaveFilter(null, LAST, null).describe())
                .isEqualTo("up to and including 2026-11-04");
    }

    @Test
    void aStatusAloneIsWrittenWithoutAComma() {
        assertThat(new LeaveFilter(null, null, LeaveStatus.APPROVED).describe()).isEqualTo("approved");
    }

    @Test
    void aPeriodAndAStatusAreBothNamed() {
        assertThat(new LeaveFilter(FIRST, LAST, LeaveStatus.PENDING).describe())
                .isEqualTo("from 2026-11-02 to 2026-11-04, waiting for hod");
    }

    @Test
    void aPeriodThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new LeaveFilter(LAST, FIRST, null))
                .isInstanceOf(LeaveRuleException.class)
                .hasMessageContaining("cannot be before the first day");
    }
}
