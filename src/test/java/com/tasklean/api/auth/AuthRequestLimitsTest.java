package com.tasklean.api.auth;

import com.tasklean.api.auth.dto.LoginRequest;
import com.tasklean.api.auth.dto.RegisterRequest;
import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.common.validation.ValidPassword;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that every string a caller can put in an auth request body is length-bounded, so an
 * oversized value is refused by validation instead of by Postgres - a 400 rather than a 500 - and
 * so a field added to one of these DTOs later cannot quietly arrive unbounded.
 */
class AuthRequestLimitsTest {

    private static final String AUTH_PACKAGE = "com.tasklean.api.auth";

    private final Validator validator = buildValidator();

    @Test
    void everyStringFieldInAnAuthRequestBody_isLengthBounded() {
        List<Class<?>> requestTypes = authRequestTypes();
        // A scan that matched nothing would otherwise pass this test for the wrong reason.
        assertThat(requestTypes).hasSizeGreaterThanOrEqualTo(15);

        List<String> unbounded = new ArrayList<>();
        for (Class<?> requestType : requestTypes) {
            for (Field field : requestType.getDeclaredFields()) {
                if (field.getType() != String.class
                        || field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                // @ValidPassword carries the policy's own maximum, so it counts as a bound.
                if (!field.isAnnotationPresent(Size.class) && !field.isAnnotationPresent(ValidPassword.class)) {
                    unbounded.add(requestType.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertThat(unbounded)
                .as("auth request fields with no @Size or @ValidPassword")
                .isEmpty();
    }

    @Test
    void register_nameLongerThanItsColumn_isRejected() {
        RegisterRequest request = registration();
        request.setName("a".repeat(FieldLimits.NAME + 1));

        assertThat(fieldsInError(request)).containsExactly("name");
    }

    @Test
    void register_middleNameLongerThanItsColumn_isRejected() {
        RegisterRequest request = registration();
        request.setMiddleName("a".repeat(FieldLimits.NAME + 1));

        assertThat(fieldsInError(request)).containsExactly("middleName");
    }

    @Test
    void register_emailAtTheMaximum_isAccepted() {
        RegisterRequest request = registration();
        request.setEmail(emailOfLength(FieldLimits.EMAIL));

        assertThat(fieldsInError(request)).isEmpty();
    }

    @Test
    void register_emailOverTheMaximum_isRejected() {
        RegisterRequest request = registration();
        request.setEmail(emailOfLength(FieldLimits.EMAIL + 1));

        assertThat(fieldsInError(request)).containsExactly("email");
    }

    @Test
    void login_passwordOverTheInputLimit_isRejected() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("a".repeat(FieldLimits.PASSWORD_INPUT + 1))
                .build();

        assertThat(fieldsInError(request)).containsExactly("password");
    }

    @Test
    void login_passwordLongerThanThePolicyAllows_isStillAccepted() {
        LoginRequest request = LoginRequest.builder()
                .email("user@example.com")
                .password("a".repeat(FieldLimits.PASSWORD + 1))
                .build();

        // Re-authentication must not lock out a password that predates the policy.
        assertThat(fieldsInError(request)).isEmpty();
    }

    private RegisterRequest registration() {
        return RegisterRequest.builder()
                .email("user@example.com")
                .password("Str0ng!Pass")
                .name("Ada")
                .lastName("Lovelace")
                .build();
    }

    private List<String> fieldsInError(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .distinct()
                .toList();
    }

    // A shape-valid address of exactly this length: the local part maxes out at the 64 characters
    // the validator allows, so the remainder is spread over domain labels of at most 63.
    private static String emailOfLength(int length) {
        StringBuilder address = new StringBuilder("a".repeat(64)).append('@');
        while (address.length() < length) {
            address.append("b".repeat(Math.min(63, length - address.length())));
            if (address.length() < length) {
                address.append('.');
            }
        }
        return address.toString();
    }

    private static List<Class<?>> authRequestTypes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Request")));
        return scanner.findCandidateComponents(AUTH_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(AuthRequestLimitsTest::load)
                .toList();
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Scanned a class that will not load: " + className, e);
        }
    }

    private static Validator buildValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }
}
