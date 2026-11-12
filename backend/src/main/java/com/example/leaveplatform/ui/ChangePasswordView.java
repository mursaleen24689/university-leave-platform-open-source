package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.leaveplatform.identity.PasswordService;
import com.example.leaveplatform.identity.WeakPasswordException;
import com.example.leaveplatform.identity.WrongPasswordException;

/**
 * Shown to anyone whose account still carries a temporary password; the
 * navigation guard sends them here from every other route. Uses the same
 * {@link PasswordService} as {@code POST /api/v1/auth/password}, so the policy
 * and the history check cannot drift between the two entry points.
 */
@Route("change-password")
@PageTitle("Change password - University Leave Platform")
@PermitAll
public class ChangePasswordView extends VerticalLayout {

    private final PasswordField current = new PasswordField("Current password");
    private final PasswordField replacement = new PasswordField("New password");
    private final PasswordField repeated = new PasswordField("Repeat new password");
    private final Paragraph message = new Paragraph();

    public ChangePasswordView(PasswordService passwords, AuthenticationContext authentication) {
        String username = authentication.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "The change-password screen needs a signed-in user"));

        current.setWidthFull();
        current.setRequiredIndicatorVisible(true);
        replacement.setWidthFull();
        replacement.setRequiredIndicatorVisible(true);
        replacement.setHelperText("At least 12 characters, not your username, "
                + "not a common password, and different from the last five passwords.");
        repeated.setWidthFull();
        repeated.setRequiredIndicatorVisible(true);

        Button submit = new Button("Change password", event -> change(passwords, username));
        Button signOut = new Button("Sign out", event -> authentication.logout());

        Image logo = new Image("logo.png", "the institution");
        logo.addClassName("app-logo");

        VerticalLayout card = new VerticalLayout(
                logo,
                new H1("Change your password"),
                new Paragraph("Your account is using a temporary password. "
                        + "Choose a new one to continue."),
                current, replacement, repeated, submit, message, signOut, new CreditFooter());
        card.addClassName("app-login-card");
        card.setPadding(false);
        card.setSpacing(false);

        addClassName("app-login-page");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        add(card);
        current.focus();
    }

    private void change(PasswordService passwords, String username) {
        message.setText("");
        if (current.isEmpty() || replacement.isEmpty() || repeated.isEmpty()) {
            message.setText("Fill in all three fields.");
            return;
        }
        if (!replacement.getValue().equals(repeated.getValue())) {
            message.setText("The two new passwords are not the same.");
            return;
        }
        try {
            passwords.change(username, current.getValue(), replacement.getValue());
        } catch (WrongPasswordException exception) {
            message.setText("The current password is not correct.");
            return;
        } catch (WeakPasswordException exception) {
            message.setText(exception.getMessage());
            return;
        }
        current.clear();
        replacement.clear();
        repeated.clear();
        getUI().ifPresent(ui -> ui.navigate(""));
    }
}
