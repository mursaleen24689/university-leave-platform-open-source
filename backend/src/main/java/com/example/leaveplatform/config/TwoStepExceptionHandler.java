package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.leaveplatform.auth.TwoStepRuleException;

/**
 * Turns a two-step action that cannot go on into a readable 400. Every
 * sentence in {@link TwoStepRuleException} is written for the person who asked,
 * so it is safe to return unchanged - and none of them comes from a sign-in
 * attempt, where the answer stays the general one on purpose.
 */
@RestControllerAdvice
class TwoStepExceptionHandler {

    @ExceptionHandler(TwoStepRuleException.class)
    ProblemDetail ruleBroken(TwoStepRuleException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Two-step signing not accepted");
        return problem;
    }
}
