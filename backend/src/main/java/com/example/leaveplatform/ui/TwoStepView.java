package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.auth.TwoStepService;

/**
 * The person's own second step: turn it on, see when it was turned on, and
 * turn it off again.
 *
 * <p>Nothing on this screen is a rule. Every refusal, every requirement and the
 * seed itself come from {@link TwoStepService}, and the screen only asks for
 * what that service needs in order to say yes: the password to start, a live
 * number from the app to finish, and both again to stop.
 *
 * <p>Two things are shown that a person might rather not have to read out to
 * anybody: the seed, in four-character groups for the app that will not scan a
 * picture, and the number the app gives back. The seed is this browser's own
 * pending setup, it is useless until a code proves the app holds it, and it is
 * never written to the database until that proof arrives - which is why it can
 * be on a screen at all. The sealed seed that does get stored is never shown
 * again, here or anywhere.
 *
 * <p>What this screen cannot do is turn two-step on for somebody else, and no
 * screen can: enrolment is the one protection that only ever moves with the
 * phone.
 */
@Route("two-step")
@PageTitle("Two-step signing - University Leave Platform")
@PermitAll
public class TwoStepView extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.UK).withZone(ZoneId.systemDefault());

    private final TwoStepService twoStep;
    private final String username;
    private final VerticalLayout panel = new VerticalLayout();

    public TwoStepView(TwoStepService twoStep, AuthenticationContext authentication) {
        this.twoStep = twoStep;
        this.username = authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "The two-step screen needs a signed-in user"));

        addClassName("app-page");
        setPadding(false);
        setSpacing(true);
        setAlignItems(Alignment.START);

        panel.addClassName("app-card");
        panel.setPadding(true);
        panel.setSpacing(true);

        add(new H2("Two-step signing"), intro(), panel);
        show();
    }

    private static Paragraph intro() {
        return new Paragraph("Two-step signing asks for your password and for a number only your own"
                + " phone can produce. It is optional: leave it off and nothing about signing in changes,"
                + " except that your account is protected by one thing instead of two.");
    }

    /** Draws whichever half applies, from what the platform already knows. */
    private void show() {
        panel.removeAll();
        TwoStepService.Status status = twoStep.status(username);
        if (status.enabled()) {
            panel.add(onPanel(status.since()));
        } else {
            panel.add(offPanel());
        }
    }

    private VerticalLayout offPanel() {
        PasswordField password = new PasswordField("Your current password");
        password.setWidthFull();
        password.setHelperText("Needed because this changes how your account is protected.");

        Button start = new Button("Set up an authenticator app", event -> Feedback.run(() -> {
            TwoStepService.Enrolment enrolment =
                    twoStep.beginEnrolment(username, password.getValue(), pending());
            showEnrolmentStep(enrolment);
        }));
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout step = new VerticalLayout(password, start);
        step.setPadding(false);
        step.setSpacing(true);
        step.addClassName("app-twostep-setup");
        return step;
    }

    /**
     * The step the person is standing at once the platform has a seed for them:
     * scan it or type it, then prove the app really took it.
     */
    void showEnrolmentStep(TwoStepService.Enrolment enrolment) {
        panel.removeAll();

        Paragraph scan = new Paragraph("Open your authenticator app on your phone, choose Add account"
                + " (or the plus button), pick Other or a similar entry, and scan this picture.");
        Div qr = new Div();
        // The picture is this server's own text: an otpauth address whose only
        // free-form parts are the issuer and this username, both percent-encoded
        // before they ever reached it. Nothing a person typed is being written
        // into the page here.
        qr.getElement().setProperty("innerHTML", enrolment.qrSvg());
        qr.addClassName("app-twostep-qr");

        TextField seed = new TextField("Or type this seed into the app");
        seed.setValue(enrolment.seedForTyping());
        seed.setReadOnly(true);
        seed.setWidthFull();
        seed.setHelperText("If the app asks for the rest by hand: SHA-1, 6 digits, every 30 seconds.");

        TextField code = new TextField("Code from the app");
        code.setPlaceholder("123456");
        code.setHelperText("Type the number the app is showing now, to prove it has the seed."
                + " Nothing is saved until that matches.");
        code.setMaxLength(9);
        code.setPattern("[0-9]*");
        code.setWidth("11rem");

        Button confirm = new Button("Turn on two-step", event -> Feedback.run(() -> {
            twoStep.confirmEnrolment(username, code.getValue(), pending());
            show();
        }));
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancel", event -> show());

        Paragraph lapse = new Paragraph("This setup is kept until " + WHEN.format(enrolment.expiresAt())
                + ". After that, start again.");

        VerticalLayout step = new VerticalLayout(scan, qr, seed, code, confirm, cancel, lapse);
        step.setPadding(false);
        step.setSpacing(true);
        panel.add(step);
        code.focus();
    }

    private VerticalLayout onPanel(Instant since) {
        Paragraph state = new Paragraph("Two-step signing has been on for your account since "
                + WHEN.format(since) + ".");
        Paragraph what = new Paragraph("Every sign-in now needs three things: your password, the"
                + " security code in the picture, and the number your app is showing. The platform"
                + " keeps only the seed your app was given, sealed so that a copy of the database"
                + " cannot be read as a second factor.");

        PasswordField password = new PasswordField("Your current password");
        password.setWidthFull();
        TextField code = new TextField("Code from the app");
        code.setMaxLength(9);
        code.setPattern("[0-9]*");
        code.setWidth("11rem");

        Button off = new Button("Turn off two-step", event -> Feedback.run(() -> {
            twoStep.turnOff(username, password.getValue(), code.getValue());
            show();
        }));
        off.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        Paragraph warning = new Paragraph("Think before you turn it off. If you lose the phone and"
                + " two-step is still on, you cannot sign in at all until the administration team"
                + " turns it off for you from the accounts list; then set it up again here on the"
                + " new phone.");

        VerticalLayout step = new VerticalLayout(state, what, password, code, off, warning);
        step.setPadding(false);
        step.setSpacing(true);
        return step;
    }

    /**
     * The browser's own session, which is where a seed in progress is kept.
     * Vaadin hands out its wrapper of the servlet session, and the wrapper
     * writes through to it - the same session that a later sign-in attempt
     * arrives on.
     */
    /**
     * The browser's own session, as the small store the service asks for.
     * Vaadin's wrapper writes through to the servlet session behind it, so a
     * seed put here is the seed this browser's next request carries. The
     * wrapper is deliberately not treated as an {@code HttpSession}: that is
     * why {@code TwoStepService} is given a store rather than a session, and
     * why this layer, not the service, knows which of the two it is holding.
     */
    TwoStepService.PendingStore pending() {
        VaadinSession current = VaadinSession.getCurrent();
        if (current == null) {
            throw new IllegalStateException("The two-step screen needs a browser session of its own.");
        }
        WrappedSession session = current.getSession();
        return new TwoStepService.PendingStore() {
            @Override
            public Object get(String key) {
                return session.getAttribute(key);
            }

            @Override
            public void put(String key, Object value) {
                session.setAttribute(key, value);
            }

            @Override
            public void drop(String key) {
                session.removeAttribute(key);
            }
        };
    }
}
