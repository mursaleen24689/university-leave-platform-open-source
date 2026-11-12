package com.example.leaveplatform.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.leaveplatform.identity.Employee;
import com.example.leaveplatform.profile.ProfileRequest.ProposedChange;

/**
 * The row itself: which fields it says it changes, and what it is willing to
 * offer to whoever applies it.
 *
 * <p>The distinction the whole feature rests on is between a column that is
 * empty because nobody was asked and a column that is empty because it is not
 * part of this request. Oracle cannot tell those two apart, so the list in
 * {@code changed_fields} has to be the only thing that decides - which is why the
 * test below writes a value straight into a column without naming the field and
 * checks that the request still refuses to offer it.
 */
class ProfileRequestTest {

    private final Employee person = new Employee(
            "EMP-1000", "Sara Ahmed", "sara.ahmed@example.edu", "0300-0000000",
            "Office Superintendent", null);

    @Test
    void aFreshRequestChangesNothingUntilAFieldIsNamed() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");

        assertThat(request.getStatus()).isEqualTo(ProfileRequestStatus.PENDING);
        assertThat(request.getStatus().isPending()).isTrue();
        assertThat(request.changedFields()).isEmpty();
        assertThat(request.changes()).isEmpty();
        assertThat(request.getDecidedAt()).isNull();
        assertThat(request.getDecidedBy()).isNull();
    }

    @Test
    void namingAFieldPutsItInTheListOnceEvenIfItIsTypedTwice() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.propose(ProfileField.PHONE, "0300-2222222");
        request.propose(ProfileField.EMAIL, "sara.k@example.edu");
        request.propose(ProfileField.PHONE, "0300-1111111");

        assertThat(request.changes())
                .extracting(change -> change.field().name())
                .containsExactly("EMAIL", "PHONE");
        assertThat(request.changes()).extracting(ProposedChange::value)
                .containsExactly("sara.k@example.edu", "0300-1111111");
    }

    /**
     * The column is only a place to keep the value. What the office is shown, and
     * what an approval writes, comes from the list - so a value that arrived in a
     * column without its field being named (a script, a hand-edited row, a future
     * bug) changes nobody's staff record.
     */
    @Test
    void aValueInAColumnThatTheListDoesNotNameIsNotPartOfTheRequest() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.propose(ProfileField.FULL_NAME, "Sara Ahmed Khan");
        write(request, "phoneProposed", "0300-9999999");
        write(request, "employeeNoProposed", "MET-7777");

        assertThat(request.changedFields()).containsExactly(ProfileField.FULL_NAME);
        assertThat(request.changes()).singleElement()
                .satisfies(change -> {
                    assertThat(change.field()).isEqualTo(ProfileField.FULL_NAME);
                    assertThat(change.value()).isEqualTo("Sara Ahmed Khan");
                });
    }

    @Test
    void aDecisionRecordsWhoMadeItAndWhenAndEndsTheWait() {
        Employee officer = anOfficer();
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.propose(ProfileField.PHONE, "0300-2222222");

        request.approve(officer, "Corrected.");

        assertThat(request.getStatus()).isEqualTo(ProfileRequestStatus.APPROVED);
        assertThat(request.getStatus().isPending()).isFalse();
        assertThat(request.getStatus().label()).isEqualTo("Approved");
        assertThat(request.getDecidedBy()).isSameAs(officer);
        assertThat(request.getDecisionNote()).isEqualTo("Corrected.");
        assertThat(request.getDecidedAt()).isNotNull();
    }

    @Test
    void withdrawingIsADecisionTooAndLeavesNoNoteBehind() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.propose(ProfileField.PHONE, "0300-2222222");

        request.cancel(person);

        assertThat(request.getStatus()).isEqualTo(ProfileRequestStatus.CANCELLED);
        assertThat(request.getStatus().label()).isEqualTo("Withdrawn");
        assertThat(request.getDecidedBy()).isSameAs(person);
        assertThat(request.getDecisionNote()).isNull();
        assertThat(request.getDecidedAt()).isNotNull();
    }

    @Test
    void refusingKeepsTheReasonInTheRowAsWellAsInTheMessage() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.reject(anOfficer(), "The number is right as it stands.");

        assertThat(request.getStatus()).isEqualTo(ProfileRequestStatus.REJECTED);
        assertThat(request.getDecidedBy().getFullName()).isEqualTo("Nadia Ahmed");
        assertThat(request.getDecisionNote()).isEqualTo("The number is right as it stands.");
    }

    /**
     * The number is written by the database (ADR-016); the copy here exists only
     * so the screen that just accepted a request can quote it. It must never
     * overwrite a number that came back from a row, because that number is what
     * the office and the person are quoting to each other.
     */
    @Test
    void theDisplayCopyOfTheNumberNeverReplacesAStoredOne() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        setId(request, 12L);
        request.stampReference();
        assertThat(request.getReference()).isEqualTo("PR-000012");

        write(request, "reference", "PR-000007");
        request.stampReference();
        assertThat(request.getReference()).isEqualTo("PR-000007");
    }

    @Test
    void aRequestWithoutAnIdHasNoNumberToShowYet() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        request.stampReference();

        assertThat(request.getReference()).isNull();
    }

    @Test
    void theListOfWhatChangesReadsBackWhatTheColumnHeld() {
        ProfileRequest request = new ProfileRequest(person, "Typo in my number.");
        write(request, "changedFields", "EMAIL,PHONE,NOT_A_FIELD");
        write(request, "emailProposed", "sara.k@example.edu");
        write(request, "phoneProposed", "0300-2222222");

        List<ProposedChange> changes = request.changes();

        assertThat(changes).hasSize(2);
        assertThat(changes).extracting(change -> change.field().name())
                .containsExactly("EMAIL", "PHONE");
    }

    private static Employee anOfficer() {
        return new Employee("MET-2000", "Nadia Ahmed", null, null, "PA to the Director", null);
    }

    private static void write(ProfileRequest request, String name, Object value) {
        try {
            Field field = ProfileRequest.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(request, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void setId(ProfileRequest request, long id) {
        write(request, "id", id);
    }
}
