package com.tasklean.api.common.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the backend password validator against the shared contract vectors in
 * {@code password-policy-vectors.json}. The same file is mirrored in the frontend and run by its
 * suite, so if either implementation drifts from the policy, the vectors fail on that side.
 */
class PasswordPolicyContractTest {

    private static final String VECTORS = "/password-policy-vectors.json";

    static Stream<Arguments> contractVectors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = PasswordPolicyContractTest.class.getResourceAsStream(VECTORS)) {
            assertThat(in).as("shared vector file present on the classpath").isNotNull();
            JsonNode root = mapper.readTree(in);
            List<Arguments> cases = new ArrayList<>();
            for (JsonNode vector : root.get("vectors")) {
                String password = textOrNull(vector.get("password"));
                String message = textOrNull(vector.get("message"));
                cases.add(Arguments.of(password, message));
            }
            return cases.stream();
        }
    }

    // JsonNode.asText() on a JSON null yields the literal "null", so map a null node to a real null.
    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    @ParameterizedTest(name = "[{index}] password={0}")
    @MethodSource("contractVectors")
    void validate_matchesSharedContract(String password, String expectedMessage) {
        assertThat(PasswordPolicyValidator.validate(password)).isEqualTo(expectedMessage);
    }
}
