package com.example.leaveplatform.delegation;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for handed-over approving: the windows naming you either side,
 * a new window of your own, closing one early, and the colleagues the picker
 * offers.
 *
 * <p>The Vaadin screen calls {@link DelegationService} directly; these exist for
 * API clients and for checking the rules from a script, which is how the live
 * verification is run. Every method takes the person from the session and never
 * from the body, so a caller cannot hand a colleague over somebody else's
 * approving. The office's reading endpoint is under
 * {@code /api/v1/admin/delegations}, where {@code config/SecurityConfig} already
 * insists on the administrator role.
 */
@RestController
@RequestMapping("/api/v1/delegations")
public class DelegationController {

    private final DelegationService delegations;

    public DelegationController(DelegationService delegations) {
        this.delegations = delegations;
    }

    @GetMapping
    List<DelegationResponse> mine(Authentication authentication) {
        return delegations.mine(authentication.getName());
    }

    /** The names the colleague picker can offer, narrowed by what the typist has typed. */
    @GetMapping("/colleagues")
    List<DelegateeResponse> colleagues(Authentication authentication,
            @RequestParam(required = false) String search) {
        return delegations.colleagues(authentication.getName(), search);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DelegationResponse create(Authentication authentication,
            @Valid @RequestBody NewDelegationRequest request) {
        return delegations.create(authentication.getName(), request);
    }

    @PostMapping("/{id}/revoke")
    DelegationResponse revoke(Authentication authentication, @PathVariable Long id) {
        return delegations.revoke(authentication.getName(), id);
    }
}
