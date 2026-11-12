package com.example.leaveplatform.profile;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The office's side of a record correction, under {@code /api/v1/admin/**} where
 * {@code config/SecurityConfig} already requires the central administrator role.
 *
 * <p>Nothing here decides whether the caller may act -
 * {@link ProfileRequestService} checks the role again, because the screen path
 * does not come through these endpoints at all and one rule has to hold for both.
 */
@RestController
@RequestMapping("/api/v1/admin/profile-requests")
class AdminProfileRequestController {

    private final ProfileRequestService requests;

    AdminProfileRequestController(ProfileRequestService requests) {
        this.requests = requests;
    }

    /**
     * The queue, longest waiting first. {@code ?waitingOnly=false} gives every
     * request instead, which is what the screen asks for when somebody wants to
     * see what has already been answered.
     */
    @GetMapping
    List<ProfileRequestResponse> queue(@RequestParam(defaultValue = "true") boolean waitingOnly) {
        return waitingOnly ? requests.queue() : requests.all();
    }

    @GetMapping("/{id}")
    ProfileRequestDetail detail(Authentication authentication, @PathVariable Long id) {
        return requests.detail(authentication.getName(), id);
    }

    @PostMapping("/{id}/approve")
    ProfileRequestResponse approve(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody ProfileDecisionRequest request) {
        return requests.approve(authentication.getName(), id, request.note());
    }

    @PostMapping("/{id}/reject")
    ProfileRequestResponse reject(Authentication authentication, @PathVariable Long id,
            @Valid @RequestBody ProfileDecisionRequest request) {
        return requests.reject(authentication.getName(), id, request.note());
    }
}
