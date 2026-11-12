package com.example.leaveplatform.delegation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * What a supervisor or a head of department asks to hand over: whom, which step,
 * and between which two days. There is no field for whose approving it is - the
 * person signed in is the only one who can give their own away - and no field
 * for the department on a final-decision window: the platform takes the one the
 * delegator heads, so a typed-in department id cannot borrow another
 * department's decisions.
 */
public record NewDelegationRequest(
        @NotNull Long toEmployeeId,
        @NotNull DelegationScope scope,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = ApprovalDelegation.MAX_REASON) String reason) {
}
