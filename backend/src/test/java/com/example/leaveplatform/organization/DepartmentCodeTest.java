package com.example.leaveplatform.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DepartmentCodeTest {

    @Test
    void trimsAndUppercases() {
        assertThat(DepartmentCode.of("  mte ").value()).isEqualTo("MTE");
        assertThat(DepartmentCode.of("sample-hr").value()).isEqualTo("SAMPLE-HR");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> DepartmentCode.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsSpacesAndSymbols() {
        assertThatThrownBy(() -> DepartmentCode.of("M T E"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DepartmentCode.of("MTE_1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooShortOrTooLong() {
        assertThatThrownBy(() -> DepartmentCode.of("M"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DepartmentCode.of("ABCDEFGHIJKLMNOPQ"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
