package com.example.leaveplatform.ui;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The role codes are what the database and the API speak; the person at the
 * screen wants the name of the job. These are the same words the app_role seed
 * puts in the database, so a screen and a database query never disagree.
 */
final class RoleLabels {

    private static final Map<String, String> PLAIN = Map.of(
            "EMPLOYEE", "Employee",
            "SUPERVISOR", "Supervisor",
            "HOD", "Head of Department",
            "PA_CLERK", "PA / Clerk",
            "DEPT_ADMIN", "Department Administrator",
            "CENTRAL_ADMIN", "Central Administrator",
            "AUDITOR", "Auditor (read-only)");

    private RoleLabels() {
    }

    /** Vaadin asks for the label of an empty item while a field starts up. */
    static String plain(String code) {
        return code == null ? null : PLAIN.getOrDefault(code, code);
    }

    static String list(Collection<String> codes) {
        return codes.stream().map(RoleLabels::plain).collect(Collectors.joining(", "));
    }
}
