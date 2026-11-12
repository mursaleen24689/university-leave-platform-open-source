package com.example.leaveplatform.leave;

import java.util.List;

/**
 * Requests waiting for the signed-in user: those sent to them as supervisor,
 * and those their department (or the whole university) still has to decide.
 */
public record ApprovalQueueResponse(
        List<LeaveApplicationResponse> supervisorQueue,
        List<LeaveApplicationResponse> decisionQueue) {
}
