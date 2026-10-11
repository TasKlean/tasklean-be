package com.tasklean.api.common.validation;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordPolicyValidatorTest {

    // A policy-compliant, non-guessable password used wherever a valid one is needed.
    private static final String VALID = "Nimbus7cloud!";

    // --- length ---

    @Test
    void validate_null_returnsChoosePasswordMessage() {
        assertThat(PasswordPolicyValidator.validate(null)).isEqualTo("Choose a password.");
    }

    @Test
    void validate_empty_returnsChoosePasswordMessage() {
        assertThat(PasswordPolicyValidator.validate("")).isEqualTo("Choose a password.");
    }

    @Test
    void validate_tooShort_returnsMinLengthMessage() {
        assertThat(PasswordPolicyValidator.validate("Aa1!bc")).isEqualTo("Use at least 8 characters.");
    }

    @Test
    void validate_tooLong_returnsMaxLengthMessage() {
        String tooLong = "Aa1!".repeat(16) + "A"; // 65 chars
        assertThat(PasswordPolicyValidator.validate(tooLong)).isEqualTo("Use no more than 64 characters.");
    }

    // --- character classes ---

    @Test
    void validate_missingUppercaseOnly_namesThatOneClass() {
        assertThat(PasswordPolicyValidator.validate("abcd12!xyz")).isEqualTo("Add an uppercase letter.");
    }

    @Test
    void validate_missingDigitAndSpecial_reportsFirstMissing() {
        // upper + lower present; digit is the first missing class
        assertThat(PasswordPolicyValidator.validate("aaaaAAAA")).isEqualTo("Add a number.");
    }

    @Test
    void validate_onlyLowercase_reportsFirstMissing() {
        // uppercase is the first missing class
        assertThat(PasswordPolicyValidator.validate("aaaaaaaa")).isEqualTo("Add an uppercase letter.");
    }

    @Test
    void validate_spaceCountsAsSpecial_passes() {
        // a space is non-alphanumeric, so it satisfies the special-character rule
        assertThat(PasswordPolicyValidator.validate("Ab1 cdef")).isNull();
    }

    // --- guessable ---

    @Test
    void validate_commonPasswordMeetingAllClasses_returnsGuessableMessage() {
        assertThat(PasswordPolicyValidator.validate("Password1!"))
                .isEqualTo("This password is too easy to guess. Try something less common.");
    }

    @Test
    void validate_strongPassword_returnsNull() {
        assertThat(PasswordPolicyValidator.validate(VALID)).isNull();
    }

    @Test
    void isGuessable_trailingTailOverCommonWord_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("password123")).isTrue();
    }

    @Test
    void isGuessable_leetSubstitutedCommonWord_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("P@ssw0rd")).isTrue();
    }

    @Test
    void isGuessable_leetAndTailCombined_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("P@ssw0rd123")).isTrue();
    }

    @Test
    void isGuessable_repeatedCharacters_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("aaaaaaaa")).isTrue();
    }

    @Test
    void isGuessable_ascendingSequence_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("abcdefgh")).isTrue();
    }

    @Test
    void isGuessable_descendingSequence_isTrue() {
        assertThat(PasswordPolicyValidator.isGuessable("87654321")).isTrue();
    }

    @Test
    void isGuessable_strongPassword_isFalse() {
        assertThat(PasswordPolicyValidator.isGuessable(VALID)).isFalse();
    }

    // --- isValid wiring (sets the per-rule message, returns the verdict) ---

    @Test
    void isValid_invalidPassword_setsMessageAndReturnsFalse() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintViolationBuilder builder = mock(ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);

        boolean result = new PasswordPolicyValidator().isValid("short", context);

        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("Use at least 8 characters.");
    }

    @Test
    void isValid_validPassword_returnsTrue() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

        assertThat(new PasswordPolicyValidator().isValid(VALID, context)).isTrue();
    }
}
