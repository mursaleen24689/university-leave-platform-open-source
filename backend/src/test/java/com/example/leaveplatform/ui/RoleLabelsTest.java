package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoleLabelsTest {

    @Test
    void everySeededRoleHasAPlainName() {
        assertThat(RoleLabels.plain("CENTRAL_ADMIN")).isEqualTo("Central Administrator");
        assertThat(RoleLabels.plain("HOD")).isEqualTo("Head of Department");
        assertThat(RoleLabels.plain("EMPLOYEE")).isEqualTo("Employee");
    }

    @Test
    void anUnknownCodeIsShownAsItIsRatherThanHidden() {
        assertThat(RoleLabels.plain("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(RoleLabels.plain(null)).isNull();
    }

    @Test
    void aListReadsAsASentenceOfNames() {
        assertThat(RoleLabels.list(List.of("EMPLOYEE", "AUDITOR")))
                .isEqualTo("Employee, Auditor (read-only)");
    }
}
