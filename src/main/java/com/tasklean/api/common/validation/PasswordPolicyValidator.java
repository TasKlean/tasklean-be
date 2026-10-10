package com.tasklean.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the account password policy for {@link ValidPassword}. Ported 1:1 from the frontend's
 * {@code validatePassword} so the API and UI agree exactly: 8-64 characters; at least one each of
 * uppercase, lowercase, digit, and a special (any non-alphanumeric) character; and not an obviously
 * guessable password. On failure it reports the same specific message the UI would show, checked in
 * the same order (empty → too short → too long → missing character classes → guessable).
 *
 * <p><strong>Mirror of</strong> {@code tasklean-fe/src/lib/validation/password.ts} — the two must
 * stay byte-for-byte in agreement (same rules, same messages). Change the policy in <em>both</em>,
 * then run both suites against the shared cases in {@code src/test/resources/password-policy-vectors.json}
 * (see {@code PasswordPolicyContractTest}), which guard against the two drifting apart.
 */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;

    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    // A short list of the obvious passwords
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123",
            "qwerty", "qwertyuiop", "12345678", "123456789", "1234567890",
            "letmein", "welcome", "iloveyou", "admin", "admin123", "football",
            "monkey", "dragon", "baseball", "sunshine", "princess", "superman",
            "trustno1", "whatever", "starwars");

    private static final Map<Character, Character> LEET = Map.of(
            '@', 'a', '4', 'a', '3', 'e', '1', 'l', '0', 'o', '5', 's', '$', 's', '7', 't', '!', 'i');

    private static final Pattern LEET_CHARS = Pattern.compile("[@43105$7!]");
    // A trailing run of digits/symbols/underscore — how most passwords "meet" the rules cheaply.
    private static final Pattern TRAILING_TAIL = Pattern.compile("[\\d\\W_]+$");

    /**
     * Validates the password and, on failure, replaces the default message with the specific
     * rule message.
     *
     * @param value   the password to validate (may be {@code null})
     * @param context the constraint context, used to set the per-rule message
     * @return {@code true} if the password satisfies the policy
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String message = validate(value);
        if (message == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

    // Returns the message to show, or null when the password is acceptable. Mirrors the frontend.
    static String validate(String value) {
        if (value == null || value.isEmpty()) {
            return "Choose a password.";
        }
        if (value.length() < MIN_LENGTH) {
            return "Use at least " + MIN_LENGTH + " characters.";
        }
        if (value.length() > MAX_LENGTH) {
            return "Use no more than " + MAX_LENGTH + " characters.";
        }

        List<String> missing = new ArrayList<>();
        if (!UPPERCASE.matcher(value).find()) {
            missing.add("an uppercase letter");
        }
        if (!LOWERCASE.matcher(value).find()) {
            missing.add("a lowercase letter");
        }
        if (!DIGIT.matcher(value).find()) {
            missing.add("a number");
        }
        if (!SPECIAL.matcher(value).find()) {
            missing.add("a special character");
        }
        if (missing.size() == 1) {
            return "Add " + missing.getFirst() + ".";
        }
        if (missing.size() > 1) {
            String head = String.join(", ", missing.subList(0, missing.size() - 1));
            return "Add " + head + " and " + missing.getLast() + ".";
        }

        // Checked last so the rule messages come first: this one is about the whole password.
        if (isGuessable(value)) {
            return "This password is too easy to guess. Try something less common.";
        }

        return null;
    }

    // Reports whether a password is one of the obvious ones, seeing through lowercasing, leet
    // substitutions, a trailing digit/symbol tail, all-same-character, and consecutive runs.
    static boolean isGuessable(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        List<String> candidates = List.of(lower, deleet(lower), stem(lower), deleet(stem(lower)));
        if (candidates.stream().anyMatch(COMMON::contains)) {
            return true;
        }
        return isRepeated(value) || isSequential(value);
    }

    // Undoes common character substitutions, so "p@ssw0rd" reads as "password".
    private static String deleet(String value) {
        Matcher matcher = LEET_CHARS.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    String.valueOf(LEET.get(matcher.group().charAt(0)))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // Strips a trailing digit-and-symbol tail, which is how most rules get met.
    private static String stem(String value) {
        return TRAILING_TAIL.matcher(value).replaceFirst("");
    }

    private static boolean isRepeated(String value) {
        return !value.isEmpty() && value.chars().distinct().count() == 1;
    }

    // Reports whether the characters run consecutively up or down the code points (e.g. "abcd", "4321").
    private static boolean isSequential(String value) {
        if (value.length() < 4) {
            return false;
        }
        int step = value.charAt(1) - value.charAt(0);
        if (step != 1 && step != -1) {
            return false;
        }
        for (int i = 2; i < value.length(); i++) {
            if (value.charAt(i) - value.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}
