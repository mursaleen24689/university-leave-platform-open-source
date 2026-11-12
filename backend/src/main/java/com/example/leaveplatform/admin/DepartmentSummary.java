package com.example.leaveplatform.admin;

import com.example.leaveplatform.organization.DepartmentType;

public record DepartmentSummary(
        Long id,
        String code,
        String name,
        String facultyCode,
        DepartmentType type,
        boolean active) {
}
