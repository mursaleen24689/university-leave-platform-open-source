package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.delegation.DelegationRuleException;

/**
 * Turns a hand-over of approving that cannot be written or cannot be used into a
 * readable 400. The message describes the caller's own window or the action
 * they were refused, so it is safe to return unchanged.
 */
@RestControllerAdvice
class DelegationExceptionHandler {

    @ExceptionHandler(DelegationRuleException.class)
    ProblemDetail ruleBroken(DelegationRuleException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Delegated approval not accepted");
        return problem;
    }
}
