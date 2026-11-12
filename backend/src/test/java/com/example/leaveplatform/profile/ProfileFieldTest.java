package com.example.leaveplatform.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.Employee;

/**
 * The list in {@code changed_fields} is the whole rule about which parts of a
 * staff record an approval is allowed to touch, so the two ways in and out of it
 * are worth checking on their own: the column has to be written the same way for
 * one field as for five, and reading it back has to be able to ignore a name that
 * is not a field at all.
 */
class ProfileFieldTest {

    @Test
    void theListIsInDeclaredOrderWhateverOrderItWasTypedIn() {
        assertThat(ProfileField.join(EnumSet.of(ProfileField.PHONE, ProfileField.EMAIL,
                ProfileField.FULL_NAME)))
                        .isEqualTo("FULL_NAME,EMAIL,PHONE");
    }

    @Test
    void anEmptyListIsAnEmptyColumnNotAWord() {
        assertThat(ProfileField.join(EnumSet.noneOf(ProfileField.class))).isEmpty();
    }

    @Test
    void aNameThePlatformDoesNotHaveIsDroppedRatherThanGuessedAt() {
        assertThat(ProfileField.parse("PHONE,DESIGNATION,DEPARTMENT,SUPERVISOR"))
                .containsExactly(ProfileField.DESIGNATION, ProfileField.PHONE);
    }

    @Test
    void nothingStoredMeansNothingChanges() {
        assertThat(ProfileField.parse(null)).isEmpty();
        assertThat(ProfileField.parse("   ")).isEmpty();
    }

    @Test
    void aColumnWrittenByHandIsReadTheSameAsOneWrittenByTheRequest() {
        assertThat(ProfileField.parse(ProfileField.join(
                EnumSet.allOf(ProfileField.class)))).containsExactlyInAnyOrder(
                        ProfileField.values());
    }

    @Test
    void theSentenceUsesTheWordsAPersonReadsNotTheCodes() {
        assertThat(ProfileField.describe(EnumSet.of(ProfileField.PHONE))).isEqualTo("phone");
        assertThat(ProfileField.describe(EnumSet.of(ProfileField.FULL_NAME, ProfileField.PHONE)))
                .isEqualTo("name and phone");
        assertThat(ProfileField.describe(EnumSet.of(ProfileField.FULL_NAME, ProfileField.EMAIL,
                ProfileField.PHONE))).isEqualTo("name, e-mail and phone");
        assertThat(ProfileField.describe(EnumSet.noneOf(ProfileField.class))).isEqualTo("nothing");
    }

    /**
     * The pair each entry carries is what makes a field-by-field approval
     * possible, so a reading or writing mistake here would silently change the
     * wrong column of the staff record.
     */
    @Test
    void eachFieldWritesItsOwnColumnAndLeavesTheRestAlone() {
        Employee person = new Employee("EMP-1000", "Sara Ahmed", "sara.ahmed@example.edu",
                "0300-0000000", "Office Superintendent", null);

        ProfileField.PHONE.writeTo(person, "0300-2222222");

        assertThat(ProfileField.PHONE.readFrom(person)).isEqualTo("0300-2222222");
        assertThat(ProfileField.FULL_NAME.readFrom(person)).isEqualTo("Sara Ahmed");
        assertThat(ProfileField.EMPLOYEE_NO.readFrom(person)).isEqualTo("EMP-1000");
        assertThat(ProfileField.EMAIL.readFrom(person)).isEqualTo("sara.ahmed@example.edu");
        assertThat(ProfileField.DESIGNATION.readFrom(person)).isEqualTo("Office Superintendent");
    }

    @Test
    void theWidthsAreTheOnesTheStaffRecordColumnsHave() {
        assertThat(ProfileField.FULL_NAME.maxLength()).isEqualTo(150);
        assertThat(ProfileField.EMPLOYEE_NO.maxLength()).isEqualTo(30);
        assertThat(ProfileField.DESIGNATION.maxLength()).isEqualTo(100);
        assertThat(ProfileField.EMAIL.maxLength()).isEqualTo(150);
        assertThat(ProfileField.PHONE.maxLength()).isEqualTo(40);
    }
}
