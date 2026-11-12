package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.profile.ProfileRuleException;

/**
 * Turns a proposed correction that cannot be accepted or cannot go on into a
 * readable 400. The message describes the caller's own request or the decision
 * they were refused, so it is safe to return unchanged.
 */
@RestControllerAdvice
class ProfileExceptionHandler {

    @ExceptionHandler(ProfileRuleException.class)
    ProblemDetail ruleBroken(ProfileRuleException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Record-change request not accepted");
        return problem;
    }
}
