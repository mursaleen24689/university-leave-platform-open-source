package com.example.leaveplatform.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.time.LocalDate;
import com.example.leaveplatform.leave.LeaveFilter;
import com.example.leaveplatform.leave.LeaveStatus;

/**
 * The period, and where it makes sense the status, that a list can be narrowed
 * to. It lives in one place because the screens that list leave each built the
 * same row separately and ended up naming the same box three different ways.
 *
 * <p>A blank part means "any", which is what the helper text says: a person who
 * wants everything must not have to know what to type in a date box.
 */
final class FilterRow extends HorizontalLayout {

    private final DatePicker from = new DatePicker("From");
    private final DatePicker to = new DatePicker("To");
    private final ComboBox<LeaveStatus> status;

    FilterRow(boolean withStatus, String applyLabel, Runnable apply) {
        from.setHelperText("Blank: any date");
        to.setHelperText("Blank: any date");
        status = withStatus ? new ComboBox<>("Status") : null;
        if (status != null) {
            status.setHelperText("Blank: any status");
            status.setItemLabelGenerator(LeaveStatus::label);
            status.setItems(LeaveStatus.values());
        }

        Button update = new Button(applyLabel, event -> apply.run());
        update.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button reset = new Button("Show everything", event -> {
            clear();
            apply.run();
        });

        // The two boxes can never disagree: picking a first day greys out the
        // earlier days in the second box, and the other way round. A period the
        // server would refuse should not be reachable by clicking.
        from.addValueChangeListener(event -> to.setMin(event.getValue()));
        to.addValueChangeListener(event -> from.setMax(event.getValue()));

        setWrap(true);
        addClassName("app-fields");
        from.setWidthFull();
        from.setMaxWidth("170px");
        to.setWidthFull();
        to.setMaxWidth("170px");
        if (status != null) {
            status.setWidthFull();
            status.setMaxWidth("220px");
        }
        add(from, to);
        if (status != null) {
            add(status);
        }
        add(update, reset);
    }

    LeaveFilter value() {
        return new LeaveFilter(from.getValue(), to.getValue(), status == null ? null : status.getValue());
    }

    LocalDate fromValue() {
        return from.getValue();
    }

    LocalDate toValue() {
        return to.getValue();
    }

    private void clear() {
        from.clear();
        to.clear();
        if (status != null) {
            status.clear();
        }
    }
}
