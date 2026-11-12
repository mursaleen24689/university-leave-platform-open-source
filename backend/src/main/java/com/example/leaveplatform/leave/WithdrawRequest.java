package com.example.leaveplatform.leave;

import jakarta.validation.constraints.Size;

/** The applicant's own note on the request they are cancelling. */
public record WithdrawRequest(
        @Size(max = 1000) String remarks) {
}
