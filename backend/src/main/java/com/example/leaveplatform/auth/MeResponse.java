package com.example.leaveplatform.auth;

import java.util.List;

public record MeResponse(
        String username,
        List<String> roles,
        String employeeNo,
        String fullName,
        String designation,
        String departmentCode,
        String departmentName,
        boolean mustChangePassword) {
}
