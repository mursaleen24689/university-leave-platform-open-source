package com.example.leaveplatform.profile;

import java.util.List;

/**
 * A request opened up: the row itself and one line per field it asks about, with
 * what the staff record says beside it.
 */
public record ProfileRequestDetail(
        ProfileRequestResponse request,
        List<ProfileFieldChange> changes) {
}
