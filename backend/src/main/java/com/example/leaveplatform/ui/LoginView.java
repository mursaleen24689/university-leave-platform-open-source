package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import java.util.Map;
import com.example.leaveplatform.auth.CaptchaService;
import com.example.leaveplatform.auth.TwoStepService;

/**
 * Sign-in screen. The form posts to Spring Security's {@code /login}; a failed
 * attempt returns here with {@code ?error}, which shows the standard message
 * without saying whether the account exists.
 *
 * <p>Above the form sits a security code, and {@code CaptchaEnforcementFilter}
 * refuses an attempt that has not answered it. The code cannot be a field of
 * this form in the normal way - Vaadin builds the sign-in form and its POST
 * contents by itself - so {@link #putCodeInSignInForm()} adds one to the form
 * that is already in the page and keeps it equal to the box the person types
 * in. The answer itself never travels to the browser and never comes back from
 * it: the screen shows a picture and only ever learns whether a typed code was
 * right. When the counts in {@code CaptchaService} are spent the picture is
 * replaced by one that names the wait, so the box explains the delay instead of
 * going blank on somebody who cannot know why.
 *
 * <p>Beside it is the optional box for an authenticator app, carried by the
 * same mechanism as the security code and checked by
 * {@code TwoStepEnforcementFilter} before the password is looked at. Whether a
 * given account needs it is not said here, and not asked here either: the box
 * is on the screen for everyone, and only an account that has two-step turned
 * on is ever refused for what is typed into it.
 */
@Route("login")
@PageTitle("Sign in - University Leave Platform")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();
    private final CaptchaService captcha;
    private final Image challenge = new Image();
    private final TextField code = new TextField();
    private final TextField appCode = new TextField();
    private final Span codeMessage = new Span();

    public LoginView(CaptchaService captcha) {
        this.captcha = captcha;

        Image logo = new Image("logo.png", "the institution");
        logo.addClassName("app-logo");

        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);

        challenge.setAlt("Security code to type");
        challenge.addClassName("app-captcha-image");
        challenge.getElement().addEventListener("click", event -> showNewChallenge());

        code.setLabel("Security code");
        code.setPlaceholder("Type the code in the picture");
        code.setId(CaptchaService.CODE_PARAMETER + "-box");
        code.setWidth("11rem");
        code.setAutocomplete(Autocomplete.OFF);

        Button refresh = new Button("New code", event -> showNewChallenge());
        refresh.addThemeVariants(ButtonVariant.LUMO_SMALL);

        codeMessage.addClassName("app-captcha-note");

        HorizontalLayout codeRow = new HorizontalLayout(challenge, code, refresh);
        codeRow.addClassName("app-captcha-row");
        codeRow.addClassName("app-fields");
        codeRow.setPadding(false);
        codeRow.setSpacing(true);
        codeRow.setWrap(true);
        codeRow.setAlignItems(Alignment.CENTER);

        // The optional second step. It is asked for in the same breath as the
        // password rather than on a screen of its own, because that is what
        // keeps the security code's counts in front of a six-digit number and
        // keeps the answer from ever saying which half went wrong.
        appCode.setLabel("Code from your app");
        appCode.setPlaceholder("Only if you use two-step");
        appCode.setId(TwoStepService.CODE_PARAMETER + "-box");
        appCode.setHelperText("Six digits from your authenticator app, for an account that uses one."
                + " Leave this empty if you have not turned two-step on.");
        appCode.setMaxLength(9);
        appCode.setWidth("11rem");
        appCode.setAutocomplete(Autocomplete.OFF);
        appCode.addClassName("app-twostep-box");

        H1 university = new H1("the institution");
        H2 platform = new H2("Leave Management Platform");

        VerticalLayout card = new VerticalLayout(logo, university, platform, codeRow, appCode,
                codeMessage, login, new CreditFooter());
        card.addClassName("app-login-card");
        card.setPadding(false);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);

        addClassName("app-login-page");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        add(card);

        putCodeInSignInForm();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> parameters = event.getLocation().getQueryParameters().getParameters();
        login.setError(parameters.containsKey("error"));
        // Every visit shows a code of its own: an attempt spends the one it was
        // shown, whether it ended in a wrong password or a wrong code.
        showNewChallenge();
        codeMessage.setText(messageFor(parameters.getOrDefault(CaptchaService.REASON_PARAMETER,
                List.of("")).get(0), parameters.containsKey("error")));
    }

    static String messageFor(String reason, boolean passwordFailed) {
        return switch (reason) {
            case CaptchaService.CODE_MISSING -> "Type the security code above, then sign in.";
            case CaptchaService.CODE_WRONG -> "That is not the security code. Type the new one shown.";
            case CaptchaService.CODE_EXPIRED -> "That security code had expired. Type the new one shown.";
            case CaptchaService.CODE_WAIT -> "Too many codes answered wrongly from this browser."
                    + " Wait for the time shown in the picture, then sign in again.";
            // One sentence for everything that is not about the picture, and it
            // names all three things without saying which one failed: a refused
            // attempt must never confirm that the password or the app code was
            // right. See docs/DECISIONS.md ADR-021.
            default -> passwordFailed
                    ? "Wrong details. Check your password, the security code in the picture and, if you"
                        + " use one, the code from your app - then type a new security code and try again."
                    : "Type the security code, then sign in.";
        };
    }

    /**
     * Puts hidden fields into the form the browser already posts, and keeps
     * each of them equal to the box above it. The form is built by the sign-in
     * web component rather than by this class, so it is looked for in the page,
     * and the retry covers the moment when this runs before that form exists.
     *
     * <p>Two fields now, in the same loop: the answer to the security code and
     * the number from an authenticator app. Neither is a field of Vaadin's
     * form, and neither is a thing this server-side class could add to it.
     */
    private void putCodeInSignInForm() {
        login.getElement().executeJs("""
                const boxes = [
                    {name: 'captcha', box: 'captcha-box'},
                    {name: 'otp', box: 'otp-box'}
                ];
                const found = () => {
                    const form = this.querySelector('form[action="login"]');
                    if (!form) {
                        return false;
                    }
                    for (const item of boxes) {
                        if (form.querySelector('input[name="' + item.name + '"]')) {
                            continue;
                        }
                        const hidden = document.createElement('input');
                        hidden.type = 'hidden';
                        hidden.name = item.name;
                        form.appendChild(hidden);
                        const typed = document.getElementById(item.box);
                        const copy = () => { hidden.value = typed ? typed.value : ''; };
                        if (typed) {
                            typed.addEventListener('input', copy);
                        }
                        form.addEventListener('submit', copy);
                    }
                    return true;
                };
                if (!found()) {
                    let tries = 0;
                    const retry = setInterval(() => {
                        if (found() || ++tries > 20) {
                            clearInterval(retry);
                        }
                    }, 50);
                }
                """);
    }

    private void showNewChallenge() {
        challenge.setSrc(captchaImageAddress(System.nanoTime()));
    }

    /**
     * The address of a picture of a code. The number only stops a browser from
     * showing an old picture; fetching this address issues the answer the
     * server then expects, so the code on screen is the code on the server.
     */
    static String captchaImageAddress(long nonce) {
        return "/api/v1/auth/captcha?i=" + nonce;
    }
}
