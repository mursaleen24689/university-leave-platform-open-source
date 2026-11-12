package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.notification.NotificationService;

/**
 * The top bar shown on every signed-in screen: university logo, the name of the
 * platform, the links this person may use, the bell, and the way out. Keeping it
 * in one place means a new screen only adds
 * {@code new BrandHeader(authentication, notifications)}.
 */
class BrandHeader extends HorizontalLayout {

    private static final Set<String> APPROVER_ROLES =
            Set.of(AppRole.SUPERVISOR, AppRole.HOD, AppRole.CENTRAL_ADMIN);

    /** A report covers other people's leave, so a supervisor is not one of them. */
    private static final Set<String> REPORT_ROLES = Set.of(AppRole.HOD, AppRole.CENTRAL_ADMIN);

    BrandHeader(AuthenticationContext authentication, NotificationService notifications) {
        String username = authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("unknown");
        Collection<String> roles = authentication.getGrantedRoles();

        Image logo = new Image("logo.png", "the institution");
        logo.addClassName("app-logo");
        Span divider = new Span();
        divider.addClassName("app-divider");
        Span product = new Span("Leave Management");
        product.addClassName("app-product");

        HorizontalLayout brand = new HorizontalLayout(logo, divider, product);
        brand.setAlignItems(Alignment.CENTER);

        HorizontalLayout nav = new HorizontalLayout();
        nav.addClassName("app-nav");
        nav.setAlignItems(Alignment.CENTER);
        nav.add(new RouterLink("Home", MainView.class), new RouterLink("My leave", MyLeaveView.class));
        if (roles.stream().anyMatch(APPROVER_ROLES::contains)) {
            nav.add(new RouterLink("Approvals", ApprovalsView.class),
                    new RouterLink("Delegations", DelegationsView.class));
        }
        if (roles.contains(AppRole.CENTRAL_ADMIN)) {
            nav.add(new RouterLink("Administration", AdminView.class),
                    new RouterLink("Leave setup", LeaveConfigView.class));
        }
        if (roles.stream().anyMatch(REPORT_ROLES::contains)) {
            nav.add(new RouterLink("Reports", ReportsView.class));
        }
        if (roles.contains(AppRole.CENTRAL_ADMIN) || roles.contains(AppRole.AUDITOR)) {
            nav.add(new RouterLink("Audit trail", AuditView.class));
        }
        nav.add(new RouterLink("My record", ProfileView.class),
                new RouterLink("Help desk", TicketsView.class),
                new RouterLink("Change password", ChangePasswordView.class));

        Span user = new Span(username);
        user.addClassName("app-user");
        user.getElement().setAttribute("title",
                "Signed in as " + username + " (" + RoleLabels.list(roles) + ")");

        Button signOut = new Button("Sign out", event -> authentication.logout());
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        VerticalLayout account = new VerticalLayout(user, signOut);
        account.addClassName("app-account");
        account.setPadding(false);
        account.setSpacing(false);

        addClassName("app-header");
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        add(brand, nav, new NotificationsBell(notifications, username), account);
        setFlexGrow(1, nav);
    }
}
