package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.AppRole;

class MainViewTest {

    @Test
    void describesAnAdministratorWithoutNamingTheRoleCode() {
        assertThat(MainView.plainRoles(List.of(AppRole.CENTRAL_ADMIN, "EMPLOYEE")))
                .contains("holiday")
                .doesNotContain("CENTRAL_ADMIN");
    }

    @Test
    void anAdministratorIsNotToldTheyOnlyDecideTheirOwnDepartment() {
        assertThat(MainView.plainRoles(List.of(AppRole.CENTRAL_ADMIN, AppRole.HOD)))
                .doesNotContain("your department");
    }

    @Test
    void aHeadOfDepartmentAndASupervisorBothGetTheirSentence() {
        String sentence = MainView.plainRoles(List.of(AppRole.HOD, AppRole.SUPERVISOR));

        assertThat(sentence).contains("decide the leave requests of your department")
                .contains("recommend the requests");
    }

    @Test
    void anOrdinaryEmployeeIsToldWhatTheyCanDo() {
        assertThat(MainView.plainRoles(List.of("EMPLOYEE")))
                .isEqualTo("you can apply for leave and follow your own requests.");
    }

    @Test
    void anAuditorSeesTheAuditTrailSentence() {
        assertThat(MainView.plainRoles(List.of(AppRole.AUDITOR)))
                .contains("record of privileged actions");
    }
}
