package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.identity.AppRole;
import com.example.leaveplatform.notification.NotificationService;

/**
 * Landing page after sign-in: one card per screen the signed-in person may use.
 */
@Route("")
@PageTitle("University Leave Platform")
@PermitAll
public class MainView extends VerticalLayout {

    private static final Set<String> APPROVER_ROLES =
            Set.of(AppRole.SUPERVISOR, AppRole.HOD, AppRole.CENTRAL_ADMIN);

    public MainView(AuthenticationContext authentication, NotificationService notifications) {
        String username = authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("unknown");
        Collection<String> roles = authentication.getGrantedRoles();

        HorizontalLayout cards = new HorizontalLayout();
        cards.addClassName("app-cards");
        cards.setWidthFull();
        cards.add(card("My leave",
                "Check your balance, request leave and follow your requests.", MyLeaveView.class));
        if (roles.stream().anyMatch(APPROVER_ROLES::contains)) {
            cards.add(card("Approvals",
                    "Recommend or decide the requests waiting for you.", ApprovalsView.class));
            cards.add(card("Delegated approvals",
                    "Hand your approving to a colleague for the days you are away, or see "
                            + "what you are holding for somebody else.", DelegationsView.class));
        }
        if (roles.contains(AppRole.CENTRAL_ADMIN)) {
            cards.add(card("Administration",
                    "Faculties, departments, staff records and sign-in accounts.", AdminView.class));
            cards.add(card("Leave setup",
                    "The holiday calendar and a corrected balance for one person.", LeaveConfigView.class));
        }
        if (roles.contains(AppRole.CENTRAL_ADMIN) || roles.contains(AppRole.AUDITOR)) {
            cards.add(card("Audit trail",
                    "Read the record of privileged actions and sign-ins.", AuditView.class));
        }
        cards.add(card("My staff record",
                "Check what the university holds about you and ask for a correction.",
                ProfileView.class));
        cards.add(card("Change password",
                "Choose a new password for your account.", ChangePasswordView.class));
        cards.add(card("Two-step signing",
                "Ask for a number from your own phone as well as your password.", TwoStepView.class));

        VerticalLayout content = new VerticalLayout(
                new H2("Welcome, " + username),
                new Paragraph("What you can do here: " + plainRoles(roles)
                        + " Use the cards below to open a screen."),
                cards,
                new CreditFooter());
        content.addClassName("app-page");
        content.setPadding(false);

        add(new BrandHeader(authentication, notifications), content);
        setPadding(false);
        setSpacing(false);
        setSizeFull();
    }

    /**
     * The role codes are what the database stores, but a person reading the
     * landing page is not interested in them; what they want to know is what
     * they are able to do.
     */
    static String plainRoles(Collection<String> roles) {
        List<String> sentences = new ArrayList<>();
        if (roles.contains(AppRole.CENTRAL_ADMIN)) {
            sentences.add("you set up the university, its staff and accounts, the holiday"
                    + " calendar, and you can decide any request.");
        } else {
            if (roles.contains(AppRole.HOD)) {
                sentences.add("you decide the leave requests of your department.");
            }
            if (roles.contains(AppRole.SUPERVISOR)) {
                sentences.add("you recommend the requests of the staff you supervise.");
            }
        }
        if (roles.contains(AppRole.AUDITOR)) {
            sentences.add("you can read the record of privileged actions.");
        }
        if (sentences.isEmpty()) {
            sentences.add("you can apply for leave and follow your own requests.");
        }
        return String.join(" ", sentences);
    }

    private static VerticalLayout card(String title, String text, Class<? extends Component> target) {
        VerticalLayout card = new VerticalLayout(
                new H3(title), new Paragraph(text), new RouterLink("Open", target));
        card.addClassName("app-card");
        card.setPadding(false);
        card.setSpacing(false);
        return card;
    }
}
