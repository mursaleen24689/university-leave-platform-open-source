package com.example.leaveplatform.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal CSV reader: comma separated, quoted fields allowed, a doubled quote
 * inside a quoted field is one quote, CRLF or LF line endings, blank lines
 * dropped. Enough for a spreadsheet export without adding a library.
 */
final class Csv {

    private Csv() {
    }

    static List<List<String>> rows(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString().trim());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString().trim());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        row.add(field.toString().trim());
        addRow(rows, row);
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.stream().allMatch(String::isEmpty);
        if (!blank) {
            rows.add(row);
        }
    }
}
