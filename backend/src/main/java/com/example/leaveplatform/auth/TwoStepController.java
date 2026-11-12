package com.example.leaveplatform.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for a person's own second step: whether it is on, the seed an
 * app is given, the code that confirms it, and the way back out.
 *
 * <p>The Vaadin screen at {@code /two-step} calls {@link TwoStepService}
 * directly; these exist for API clients and for checking the rules from a
 * script, which is how the live verification is run.
 *
 * <p>Every method takes the person from the session and never from the body, so
 * nobody can enrol, confirm or unroll a colleague's authenticator. Nothing here
 * is open to a visitor without an account: an enrolment is a change to how an
 * account is protected, and that is not something a stranger is owed an opinion
 * about. The sealed seed is never returned either - the seed in these answers is
 * the one this person is being asked to show their app, and it is useless to
 * anybody whose app did not scan it.
 */
@RestController
@RequestMapping("/api/v1/auth/two-step")
public class TwoStepController {

    public record PasswordRequest(@NotBlank String password) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record TurnOffRequest(@NotBlank String password, @NotBlank String code) {
    }

    public record EnrolmentResponse(String seed, String otpAuthUri, Instant expiresAt) {
    }

    public record StatusResponse(boolean enabled, Instant since) {
    }

    private final TwoStepService twoStep;

    public TwoStepController(TwoStepService twoStep) {
        this.twoStep = twoStep;
    }

    @GetMapping
    StatusResponse status(Authentication authentication) {
        TwoStepService.Status status = twoStep.status(authentication.getName());
        return new StatusResponse(status.enabled(), status.since());
    }

    /** A seed to show an app, held in this session until a code proves the app took it. */
    @PostMapping("/enrolment")
    EnrolmentResponse begin(Authentication authentication, HttpSession session,
            @Valid @RequestBody PasswordRequest request) {
        TwoStepService.Enrolment enrolment = twoStep.beginEnrolment(authentication.getName(),
                request.password(), TwoStepService.PendingStore.of(session));
        return new EnrolmentResponse(enrolment.seed(), enrolment.otpAuthUri(), enrolment.expiresAt());
    }

    @PostMapping("/enrolment/confirm")
    StatusResponse confirm(Authentication authentication, HttpSession session,
            @Valid @RequestBody CodeRequest request) {
        TwoStepService.Status status = twoStep.confirmEnrolment(authentication.getName(),
                request.code(), TwoStepService.PendingStore.of(session));
        return new StatusResponse(status.enabled(), status.since());
    }

    @PostMapping("/off")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void turnOff(Authentication authentication, @Valid @RequestBody TurnOffRequest request) {
        twoStep.turnOff(authentication.getName(), request.password(), request.code());
    }
}
