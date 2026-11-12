package com.example.leaveplatform.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.function.Consumer;

/**
 * A confirm box with an optional remark, used wherever a decision or a
 * destructive action should be explained before it is saved. The action runs
 * through {@link Feedback}, so a refused save leaves the person the reason
 * instead of a silent failure.
 */
final class RemarkDialog {

    private RemarkDialog() {
    }

    static void open(String heading, String helpText, String confirmLabel, String successMessage,
            Consumer<String> action) {
        TextArea remarks = new TextArea("Remarks (optional)");
        remarks.setWidthFull();
        confirm(heading, helpText, remarks, confirmLabel, successMessage,
                () -> action.accept(remarks.getValue()));
    }

    /**
     * The same box where the action takes no explanation, so no field asking for
     * one is shown.
     */
    static void confirm(String heading, String helpText, String confirmLabel, String successMessage,
            Runnable action) {
        confirm(heading, helpText, null, confirmLabel, successMessage, action);
    }

    private static void confirm(String heading, String helpText, Component field, String confirmLabel,
            String successMessage, Runnable action) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(heading);
        if (field != null) {
            dialog.add(field);
        }
        if (helpText != null) {
            Span help = new Span(helpText);
            help.addClassName("app-dialog-help");
            dialog.add(help);
        }

        Button cancel = new Button("Cancel", event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button confirm = new Button(confirmLabel, event -> {
            dialog.close();
            Feedback.run(successMessage, action);
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }
}
