package com.example.leaveplatform.profile;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for the staff side of a record correction: what the university
 * holds about you, what you would like it to say, and what you asked before.
 *
 * <p>The Vaadin screen calls {@link ProfileRequestService} directly; these exist
 * for API clients and for checking the rules from a script, which is how the live
 * verification is run.
 *
 * <p>Every method takes the person from the session and never from the body, so a
 * caller cannot ask for a colleague's record to be corrected or withdraw a request
 * that is not theirs. The office's own endpoints are under
 * {@code /api/v1/admin/profile-requests}, where {@code config/SecurityConfig}
 * already insists on the administrator role.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileRequestService requests;

    public ProfileController(ProfileRequestService requests) {
        this.requests = requests;
    }

    @GetMapping
    List<ProfileRequestResponse> mine(Authentication authentication) {
        return requests.mine(authentication.getName());
    }

    /** What the staff record says now, which is what the form is asking to change. */
    @GetMapping("/record")
    ProfileRecordResponse record(Authentication authentication) {
        return requests.record(authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProfileRequestResponse submit(Authentication authentication,
            @Valid @RequestBody ProfileChangesRequest request) {
        return requests.submit(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    ProfileRequestDetail detail(Authentication authentication, @PathVariable Long id) {
        return requests.detail(authentication.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    ProfileRequestResponse cancel(Authentication authentication, @PathVariable Long id) {
        return requests.cancel(authentication.getName(), id);
    }
}
