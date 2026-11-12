package com.example.leaveplatform.leave;

public record LeaveBalanceResponse(
        String leaveTypeCode,
        String leaveTypeName,
        int year,
        int allocated,
        int used,
        int remaining) {
}
