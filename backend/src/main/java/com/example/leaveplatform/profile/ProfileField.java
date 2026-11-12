package com.example.leaveplatform.profile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.example.leaveplatform.identity.Employee;

/**
 * The five parts of a staff record a person may propose a correction to, in the
 * order a screen lists them.
 *
 * <p>Each entry knows how to read and write its own column on {@link Employee},
 * which is what lets the service apply a request field by field without a list of
 * columns to keep in step. The mutators are the single-field ones in
 * {@code identity/Employee}, not {@code updateDetails}: the latter replaces all
 * five values at once, and a request that names only a phone number has nothing
 * to say about the rest.
 *
 * <p>A department transfer and a change of reporting line are not here on
 * purpose: they decide whose leave requests a person sends and whose approvals
 * reach them, so the office makes them from the organisation screen, where the
 * destination is chosen from a list rather than typed.
 *
 * <p>The names are stored in {@code profile_request.changed_fields} and are the
 * words the audit trail and the office read, so they are fixed here instead of
 * free text: a second spelling of the same field would be a second rule to apply.
 */
public enum ProfileField {

    FULL_NAME("Name", 150, Employee::getFullName, Employee::changeFullName),
    EMPLOYEE_NO("Staff number", 30, Employee::getEmployeeNo, Employee::changeEmployeeNo),
    DESIGNATION("Designation", 100, Employee::getDesignation, Employee::changeDesignation),
    EMAIL("E-mail", 150, Employee::getEmail, Employee::changeEmail),
    PHONE("Phone", 40, Employee::getPhone, Employee::changePhone);

    private final String label;
    private final int maxLength;
    private final Function<Employee, String> reader;
    private final BiConsumer<Employee, String> writer;

    ProfileField(String label, int maxLength, Function<Employee, String> reader,
            BiConsumer<Employee, String> writer) {
        this.label = label;
        this.maxLength = maxLength;
        this.reader = reader;
        this.writer = writer;
    }

    /** The words a person reads; the code is what the database keeps. */
    public String label() {
        return label;
    }

    /** The column width on the staff record, so a screen refuses before the database does. */
    public int maxLength() {
        return maxLength;
    }

    /** What the record says about this field right now, which may be nothing. */
    public String readFrom(Employee person) {
        return reader.apply(person);
    }

    /** Writes this one field and no other. */
    public void writeTo(Employee person, String value) {
        writer.accept(person, value);
    }

    /** The list as the request row stores it, in the order the fields are declared. */
    public static String join(EnumSet<ProfileField> fields) {
        List<String> names = new ArrayList<>();
        for (ProfileField field : values()) {
            if (fields.contains(field)) {
                names.add(field.name());
            }
        }
        return String.join(",", names);
    }

    /**
     * The list read back. An unknown name in the column is dropped rather than
     * guessed at: it can only have arrived from outside the platform, and a field
     * the platform does not know must not be applied to a staff record.
     */
    public static EnumSet<ProfileField> parse(String stored) {
        EnumSet<ProfileField> fields = EnumSet.noneOf(ProfileField.class);
        if (stored == null || stored.isBlank()) {
            return fields;
        }
        for (String name : stored.split(",")) {
            try {
                fields.add(valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // A name the platform does not have changes nothing.
            }
        }
        return fields;
    }

    /** "name, e-mail and phone" - the sentence the office and the audit trail read. */
    public static String describe(EnumSet<ProfileField> fields) {
        List<String> labels = new ArrayList<>();
        for (ProfileField field : values()) {
            if (fields.contains(field)) {
                labels.add(field.label.toLowerCase(Locale.ROOT));
            }
        }
        if (labels.isEmpty()) {
            return "nothing";
        }
        if (labels.size() == 1) {
            return labels.get(0);
        }
        return String.join(", ", labels.subList(0, labels.size() - 1))
                + " and " + labels.get(labels.size() - 1);
    }
}
