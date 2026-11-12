package com.example.leaveplatform.admin;

import java.util.List;

/**
 * Outcome of a bulk import. Rows with a validation problem are reported and
 * skipped; the rest of the file is still applied.
 */
public record ImportResult(int created, int updated, int failed, List<String> messages) {
}
