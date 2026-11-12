package com.example.leaveplatform.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void readsPlainRows() {
        assertThat(Csv.rows("a,b\nc,d"))
                .containsExactly(List.of("a", "b"), List.of("c", "d"));
    }

    @Test
    void keepsCommasInsideQuotes() {
        assertThat(Csv.rows("1,\"Khan, Ayesha\",Clerk"))
                .containsExactly(List.of("1", "Khan, Ayesha", "Clerk"));
    }

    @Test
    void doubledQuoteInsideQuotesIsOneQuote() {
        assertThat(Csv.rows("\"say \"\"hello\"\"\",x"))
                .containsExactly(List.of("say \"hello\"", "x"));
    }

    @Test
    void acceptsWindowsLineEndingsAndDropsBlankLines() {
        assertThat(Csv.rows("a,b\r\n\r\n   \r\nc,d\r\n"))
                .containsExactly(List.of("a", "b"), List.of("c", "d"));
    }

    @Test
    void trimsUnquotedFields() {
        assertThat(Csv.rows(" a , b \n"))
                .containsExactly(List.of("a", "b"));
    }

    @Test
    void keepsShortRowsAsTheyAre() {
        assertThat(Csv.rows("x\nx,y"))
                .containsExactly(List.of("x"), List.of("x", "y"));
    }

    @Test
    void returnsNothingForMissingOrBlankText() {
        assertThat(Csv.rows(null)).isEmpty();
        assertThat(Csv.rows("")).isEmpty();
        assertThat(Csv.rows("  \n \r\n")).isEmpty();
    }
}
