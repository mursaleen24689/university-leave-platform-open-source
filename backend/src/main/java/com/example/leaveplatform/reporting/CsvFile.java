package com.example.leaveplatform.reporting;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A spreadsheet file ready to download. Written here rather than in the screen
 * so the same rules apply to every export: CRLF line ends and a byte-order mark,
 * which is what Excel on a Windows desktop expects, and a value that would be
 * read back as a formula is neutralised before it is written.
 */
public record CsvFile(String fileName, byte[] content) {

    public static CsvFile of(String fileName, List<String> headers, List<List<String>> rows) {
        StringBuilder text = new StringBuilder("﻿");
        appendLine(text, headers);
        for (List<String> row : rows) {
            appendLine(text, row);
        }
        return new CsvFile(fileName, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendLine(StringBuilder text, List<String> values) {
        for (int position = 0; position < values.size(); position++) {
            if (position > 0) {
                text.append(',');
            }
            text.append(escape(values.get(position)));
        }
        text.append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        // A leading = + - @ makes a spreadsheet run the cell as a formula, so a
        // file someone else opened could execute something on their machine.
        // Tab and carriage return are in the set because Excel strips them
        // before it decides, which would otherwise slip a formula past this.
        String safe = value.isEmpty() || "=+-@\t\r".indexOf(value.charAt(0)) < 0 ? value : "'" + value;
        boolean needsQuotes = safe.chars().anyMatch(c -> c == ',' || c == '"' || c == '\n' || c == '\r');
        return needsQuotes ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }
}
