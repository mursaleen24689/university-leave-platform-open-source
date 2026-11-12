package com.example.leaveplatform.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.example.leaveplatform.identity.AppUser;
import com.example.leaveplatform.identity.AppUserRepository;

/**
 * Sends an account that still has a temporary password to
 * {@link ChangePasswordView}, whichever page it asks for.
 *
 * <p>The flag is read from the database on each navigation rather than from the
 * session principal, which is loaded once at sign-in and would stay stale after
 * the password is changed. Navigation happens a few times per session, so the
 * extra lookup is cheap; if that ever changes, cache it per request rather than
 * per session.
 */
@Component
class PasswordChangeGuard implements VaadinServiceInitListener {

    private final AppUserRepository users;

    PasswordChangeGuard(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(this::beforeEnter));
    }

    private void beforeEnter(BeforeEnterEvent event) {
        String username = signedInUsername();
        if (username == null || event.getNavigationTarget() == ChangePasswordView.class) {
            return;
        }
        if (requiresChange(username)) {
            event.rerouteTo(ChangePasswordView.class);
        }
    }

    boolean requiresChange(String username) {
        return users.findByUsername(username)
                .map(AppUser::isMustChangePassword)
                .orElse(false);
    }

    static String signedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
