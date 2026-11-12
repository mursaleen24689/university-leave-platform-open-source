package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.example.leaveplatform.mail.MailAttempt;
import com.example.leaveplatform.mail.MailService;
import com.example.leaveplatform.mail.MailSettingsInput;
import com.example.leaveplatform.mail.MailSettingsService;
import com.example.leaveplatform.mail.MailSettingsSummary;
import com.example.leaveplatform.notification.NotificationKind;

/**
 * The mail settings, for the central administrator only.
 *
 * <p>The university's mail credentials are typed here when somebody is given
 * them; nothing is hard-coded and nothing is read back. The password field is
 * write-only by design: after a save it goes blank again and the note says only
 * whether a password is stored, so the screen can be left open on a shared
 * computer without showing a secret.
 *
 * <p>Sending stays switched off until the administrator turns it on, so filling
 * in a host name cannot start mail going out before the details have been
 * checked with the test button.
 */
class MailSection extends VerticalLayout {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final MailSettingsService settings;
    private final MailService mail;
    private final String actor;

    private final Checkbox enabled = new Checkbox("Send mail");
    private final TextField host = new TextField("Mail server");
    private final IntegerField port = new IntegerField("Port");
    private final TextField username = new TextField("Sign-in name on the mail server");
    private final PasswordField password = new PasswordField("Password");
    private final TextField fromAddress = new TextField("Sender address");
    private final Checkbox useTls = new Checkbox("Use TLS (STARTTLS)");
    private final CheckboxGroup<String> kinds = new CheckboxGroup<>("Mail these kinds of message");
    private final Paragraph passwordNote = new Paragraph();
    private final Button save = new Button("Save settings");

    private final TextField testAddress = new TextField("Send a test to");
    private final Button test = new Button("Send a test");
    private final Paragraph testResult = new Paragraph();
    private final Paragraph logNote = new Paragraph();
    private final VerticalLayout log = new VerticalLayout();

    MailSection(MailSettingsService settings, MailService mail, String actor) {
        this.settings = settings;
        this.mail = mail;
        this.actor = actor;

        add(new H3("E-mail notifications"), description(), fields(), testRow(), testResult,
                passwordNote, logNote, log);
        addClassName("app-card");
        setWidthFull();
        setPadding(false);
        setSpacing(true);

        save.addClickListener(event -> Feedback.run(this::saveSettings));
        test.addClickListener(event -> sendTest());
        refresh();
    }

    private static Paragraph description() {
        return new Paragraph("Nothing needs to be filled in here for the platform to work: the bell in the"
                + " header already carries every message. This screen is for when the administration wants"
                + " the same news in the person's inbox as well. Until the switch is turned on, the"
                + " platform never contacts a mail server.");
    }

    private VerticalLayout fields() {
        host.setPlaceholder("mail.example.edu");
        host.setWidthFull();
        port.setMin(1);
        port.setMax(65535);
        port.setWidth("9rem");
        port.setMaxWidth("100%");
        password.setPlaceholder("Type only to change it");
        fromAddress.setPlaceholder("leave@example.edu");
        fromAddress.setWidthFull();
        kinds.setItems(Arrays.stream(NotificationKind.values()).map(Enum::name).toList());
        kinds.setItemLabelGenerator(ActionLabels::plain);

        HorizontalLayout serverRow = new HorizontalLayout(host, port);
        serverRow.setWidthFull();
        serverRow.setPadding(false);
        serverRow.getStyle().set("flex-wrap", "wrap");
        serverItem(host);
        VerticalLayout form = new VerticalLayout(enabled, serverRow, username, password, fromAddress,
                useTls, kinds, save);
        form.setPadding(false);
        form.setSpacing(true);
        form.setWidthFull();
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return form;
    }

    private static void serverItem(TextField field) {
        field.setMinWidth("14rem");
        field.getStyle().set("flex", "1 1 auto");
    }

    private HorizontalLayout testRow() {
        testAddress.setWidthFull();
        testAddress.setMinWidth("16rem");
        HorizontalLayout row = new HorizontalLayout(testAddress, test);
        row.setAlignItems(FlexComponent.Alignment.END);
        row.setPadding(false);
        row.setWidthFull();
        testResult.addClassNames("app-muted");
        return row;
    }

    private void saveSettings() {
        boolean keptPassword = settings.summary().hasPassword() && password.getValue().isBlank();
        Set<String> chosen = new LinkedHashSet<>(kinds.getSelectedItems());
        settings.update(new MailSettingsInput(enabled.getValue(), host.getValue(), port.getValue(),
                username.getValue(), password.getValue().isBlank() ? null : password.getValue(),
                fromAddress.getValue(), useTls.getValue(), chosen, false), actor);
        password.setValue("");
        refresh();
        Feedback.success(keptPassword ? "Settings saved. The stored password was kept." : "Settings saved.");
    }

    private void sendTest() {
        String address = testAddress.getValue() == null ? "" : testAddress.getValue().trim();
        if (address.isEmpty()) {
            testResult.setText("Type an address to send the test to.");
            return;
        }
        testResult.setText(mail.send(address, "university leave platform test",
                "This is a test from the leave platform. If you can read it, the settings work."));
        testAddress.setValue("");
        refreshLog();
    }

    private void refresh() {
        MailSettingsSummary current = settings.summary();
        enabled.setValue(current.enabled());
        host.setValue(text(current.host()));
        port.setValue(current.port());
        username.setValue(text(current.username()));
        fromAddress.setValue(text(current.fromAddress()));
        useTls.setValue(current.useTls());
        kinds.setValue(current.mailedEvents());
        password.setValue("");
        passwordNote.setText(passwordNoteFor(current));
        refreshLog();
    }

    private void refreshLog() {
        List<MailAttempt> attempts = mail.recentAttempts();
        log.removeAll();
        if (attempts.isEmpty()) {
            logNote.setText("The platform has not tried to send mail yet.");
            return;
        }
        logNote.setText("The last " + attempts.size() + " mail attempts:");
        for (MailAttempt attempt : attempts) {
            // The log holds the mail server's whole answer, which runs to
            // several sentences; the screen shows the beginning of it and the
            // audit trail and database keep the rest.
            String detail = attempt.getDetail();
            String words = detail == null ? "" : " (" + shorten(detail) + ")";
            Paragraph row = new Paragraph(WHEN.format(attempt.getAttemptedAt()) + " - "
                    + attempt.getRecipient() + " - " + outcomeWords(attempt) + words);
            row.addClassName("app-log-line");
            log.add(row);
        }
    }

    private static String shorten(String detail) {
        return detail.length() <= 180 ? detail : detail.substring(0, 180) + "...";
    }

    private static String outcomeWords(MailAttempt attempt) {
        return switch (attempt.getOutcome()) {
            case MailAttempt.SENT -> "sent";
            case MailAttempt.SKIPPED -> "not sent";
            default -> "failed";
        };
    }

    private static String passwordNoteFor(MailSettingsSummary current) {
        if (!current.keyIsConfigured()) {
            return "The platform cannot store a mail password yet: the person who runs the server has to"
                    + " set LEAVE_MAIL_KEY first. Host, port and addresses save normally without it.";
        }
        return current.hasPassword()
                ? "A password is saved. Leave the field blank to keep it, or type a new one to replace it."
                : "No password is saved yet.";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
