package com.example.leaveplatform.admin;

public record EmployeeSummary(
        Long id,
        String employeeNo,
        String fullName,
        String designation,
        Long departmentId,
        String departmentCode,
        String departmentName,
        String email,
        String phone,
        Long supervisorId,
        boolean active) {
}
