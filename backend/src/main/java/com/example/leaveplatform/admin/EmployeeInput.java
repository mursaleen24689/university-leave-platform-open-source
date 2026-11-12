package com.example.leaveplatform.admin;

/**
 * The editable part of an employee record. Only full name, designation and
 * department are required; the rest may arrive later, which is why they are
 * allowed to be null or blank.
 */
public record EmployeeInput(
        String employeeNo,
        String fullName,
        String designation,
        Long departmentId,
        Long supervisorId,
        String email,
        String phone) {
}
