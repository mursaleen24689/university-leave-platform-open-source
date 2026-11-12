package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActionLabelsTest {

    @Test
    void aCodeReadsAsWords() {
        assertThat(ActionLabels.plain("LEAVE_APPROVED")).isEqualTo("Leave approved");
        assertThat(ActionLabels.plain("SIGNED_IN")).isEqualTo("Signed in");
        assertThat(ActionLabels.plain("PUBLIC_HOLIDAY")).isEqualTo("Public holiday");
    }

    @Test
    void anEmptyOrMissingValueDoesNotBreakTheCell() {
        assertThat(ActionLabels.plain(null)).isEmpty();
        assertThat(ActionLabels.plain("  ")).isEmpty();
    }
}
