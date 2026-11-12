package com.example.leaveplatform.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvFileTest {

    private static final List<String> HEADINGS = List.of("Name", "Notes");

    @Test
    void aNameWithACommaOrAQuoteStaysInsideOneCell() {
        CsvFile file = write("Khan, Ali", "He said \"ok\"");

        assertThat(dataLine(file)).isEqualTo("\"Khan, Ali\",\"He said \"\"ok\"\"\"");
    }

    @Test
    void aLineBreakInsideAReasonDoesNotStartANewRow() {
        CsvFile file = write("Khan Ali", "one\ntwo");

        assertThat(new String(file.content(), StandardCharsets.UTF_8))
                .endsWith("Khan Ali,\"one\ntwo\"\r\n");
        assertThat(dataLine(file)).isEqualTo("Khan Ali,\"one\ntwo\"");
    }

    @Test
    void aValueThatLooksLikeAFormulaIsNotRunByTheSpreadsheet() {
        // A cell starting with = + - @ is executed when the file is opened, so
        // an exported reason could run something on the reader's machine.
        CsvFile file = write("Khan Ali", "=HYPERLINK(\"http://example.com\")");

        assertThat(dataLine(file))
                .isEqualTo("Khan Ali,\"'=HYPERLINK(\"\"http://example.com\"\")\"");
    }

    @Test
    void aFormulaHiddenBehindATabOrACarriageReturnIsStillNeutralised() {
        // Excel strips a leading tab or carriage return before it decides whether
        // the cell is a formula, so guarding only = + - @ would let these through.
        assertThat(dataLine(write("Khan Ali", "\t=1+1"))).isEqualTo("Khan Ali,'\t=1+1");
        assertThat(dataLine(write("Khan Ali", "\r=1+1"))).isEqualTo("Khan Ali,\"'\r=1+1\"");
    }

    @Test
    void anAbsentValueBecomesAnEmptyCell() {
        CsvFile file = write("Khan Ali", null);

        assertThat(dataLine(file)).isEqualTo("Khan Ali,");
    }

    @Test
    void theFileIsWhatExcelOnADesktopMachineExpects() {
        CsvFile file = write("Khan Ali", "note");
        byte[] bytes = file.content();

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\r\n");
    }

    private static CsvFile write(String name, String notes) {
        // Arrays.asList, not List.of: a value that is absent is one of the
        // things an export has to survive.
        return CsvFile.of("test.csv", HEADINGS, List.of(Arrays.asList(name, notes)));
    }

    private static String dataLine(CsvFile file) {
        String text = new String(file.content(), StandardCharsets.UTF_8).replace("﻿", "");
        return text.split("\r\n")[1];
    }
}
