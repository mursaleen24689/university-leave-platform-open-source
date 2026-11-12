package com.example.leaveplatform.profile;

import com.example.leaveplatform.identity.Employee;

/** Whose record it is and what it says now, for the screen that proposes a correction. */
public record ProfileRecordResponse(
        String employeeNo,
        String fullName,
        String designation,
        String email,
        String phone,
        String departmentName) {

    public static ProfileRecordResponse of(Employee person) {
        return new ProfileRecordResponse(
                person.getEmployeeNo(),
                person.getFullName(),
                person.getDesignation(),
                person.getEmail(),
                person.getPhone(),
                person.getDepartment().getName());
    }
}
