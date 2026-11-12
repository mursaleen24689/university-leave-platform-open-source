package com.example.leaveplatform.profile;

import jakarta.validation.constraints.Size;

/**
 * What a member of staff would like their record to say. A field left out - null
 * or blank - is not part of the request and will not be touched; only the fields
 * with a value in them are proposed. That is the rule the row keeps as
 * {@code changed_fields}, and it is why this record has no required fields at
 * all: a person usually has one thing to correct, not five.
 *
 * <p>There is no way to ask for a field to be emptied. Removing a staff number or
 * an e-mail address is the office's decision, made on the employee record.
 */
public record ProfileChangesRequest(
        @Size(max = 150) String fullName,
        @Size(max = 30) String employeeNo,
        @Size(max = 100) String designation,
        @Size(max = 150) String email,
        @Size(max = 40) String phone,
        @Size(max = ProfileRequest.MAX_REASON) String reason) {
}
