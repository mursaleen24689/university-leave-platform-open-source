package com.example.leaveplatform.organization;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        DepartmentType type,
        String facultyCode,
        String facultyName,
        boolean active) {
}
