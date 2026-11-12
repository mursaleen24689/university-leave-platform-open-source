package com.example.leaveplatform.leave;

import jakarta.validation.constraints.Size;

public record SupervisorDecisionRequest(
        boolean recommend,
        @Size(max = 1000) String remarks) {
}
