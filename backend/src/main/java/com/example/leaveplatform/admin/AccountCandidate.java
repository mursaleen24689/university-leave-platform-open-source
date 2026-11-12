package com.example.leaveplatform.admin;

/**
 * An employee who has no account yet, offered in the account form.
 */
public record AccountCandidate(Long employeeId, String employeeNo, String fullName, String departmentCode) {

    public String label() {
        return employeeNo == null || employeeNo.isBlank()
                ? fullName + " (" + departmentCode + ")"
                : fullName + " (" + employeeNo + ", " + departmentCode + ")";
    }
}
