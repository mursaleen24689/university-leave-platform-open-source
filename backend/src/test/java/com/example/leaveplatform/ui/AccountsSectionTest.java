package com.example.leaveplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountsSectionTest {

    @Test
    void buildsAUsernameFromTheFullName() {
        assertThat(AccountsSection.suggest("Ayesha Khan")).isEqualTo("ayesha.khan");
    }

    @Test
    void collapsesRepeatedSpacesAndTrims() {
        assertThat(AccountsSection.suggest("  Sara  Ul  Ahmed ")).isEqualTo("sara.ul.ahmed");
    }

    @Test
    void keepsDotsAndDropsOtherPunctuation() {
        assertThat(AccountsSection.suggest("Dr. A. Khan (Registrar)")).isEqualTo("dr.a.khan");
    }

    @Test
    void limitsTheLengthToSixtyCharacters() {
        assertThat(AccountsSection.suggest("a".repeat(80))).hasSize(60);
    }

    @Test
    void returnsAnEmptySuggestionWhenNothingUsableIsLeft() {
        assertThat(AccountsSection.suggest(null)).isEmpty();
        assertThat(AccountsSection.suggest("   ")).isEmpty();
    }
}
