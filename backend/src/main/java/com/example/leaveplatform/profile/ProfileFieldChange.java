package com.example.leaveplatform.profile;

/**
 * One line of the comparison the office decides on: the field, what the staff
 * record says at this moment, and what was proposed.
 *
 * <p>The current value is read when somebody looks, not stored with the request.
 * A snapshot taken when the request was written would go stale the moment the
 * office corrected the record by hand, and approving against it would then
 * overwrite a change nobody is being asked about.
 */
public record ProfileFieldChange(String field, String label, String current, String proposed) {

    public static ProfileFieldChange of(ProfileField field, String current, String proposed) {
        return new ProfileFieldChange(field.name(), field.label(), current, proposed);
    }
}
