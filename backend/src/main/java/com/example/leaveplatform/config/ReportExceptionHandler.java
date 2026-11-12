package com.example.leaveplatform.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import com.example.leaveplatform.reporting.ReportController;

/**
 * Turns a refused report or a refused printed form into a readable answer.
 *
 * <p>It is scoped to the reporting endpoints only, because the reporting
 * service states its refusals in sentences for the person at the screen - a
 * month out of range, a department that is not theirs to report on, a code that
 * does not match - and {@code server.error.include-message} keeps those
 * sentences out of the default error body. The messages name the caller's own
 * action and nothing else, so they are safe to return unchanged; the
 * verification refusal in particular says only that the pair did not match,
 * which is what the platform is willing to tell anybody.
 */
@RestControllerAdvice(assignableTypes = ReportController.class)
class ReportExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail ruleBroken(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
        problem.setTitle("Report not produced");
        return problem;
    }
}
