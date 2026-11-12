package com.example.leaveplatform.delegation;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The office's reading of the delegation register, under {@code /api/v1/admin/**}
 * where {@code config/SecurityConfig} already requires the central administrator
 * role.
 *
 * <p>Reading only. The office can see every window and can close one that
 * outlived its welcome - {@link DelegationService#revoke} lets it - but it
 * cannot open a window over anybody's approving: whose authority is handed over
 * is a decision only the person holding it can make.
 */
@RestController
@RequestMapping("/api/v1/admin/delegations")
class AdminDelegationController {

    private final DelegationService delegations;

    AdminDelegationController(DelegationService delegations) {
        this.delegations = delegations;
    }

    @GetMapping
    List<DelegationResponse> all(Authentication authentication) {
        return delegations.all(authentication.getName());
    }
}
