package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.category.dto.CategoryRequest;
import com.tasklean.api.domain.group.dto.GroupRequest;
import com.tasklean.api.domain.tag.dto.TagRequest;
import com.tasklean.api.domain.task.dto.TaskRequest;
import com.tasklean.api.domain.user.dto.UserRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The register of what the audit trail keeps, and the guard against a request field nobody
 * classified. Every field of an update's DTO belongs in exactly one of three buckets, and adding
 * one fails here until somebody puts it in a bucket - a new field wired into an update without a
 * tracking call audits as nothing at all, which no behavioural test sees.
 *
 * <p>The buckets carry a privacy decision, so this file is the one place to read for "which values
 * does the trail persist": <b>values shown</b> keeps the field's before and after, which live
 * forever in an append-only table and are visible to every reader of the entry; <b>names only</b>
 * records that the field moved and nothing more; <b>untracked</b> means the update ignores it.
 * The service tests prove the reported names and values match these lists. Two tracked fields are
 * absent because they have no DTO: the group and platform {@code role} changes take an enum, and
 * {@code inviteCode} is rotated by an endpoint with no body.
 */
class AuditTrackedFieldsTest {

    @Test
    void taskRequest_everyField_isClassified() {
        assertEveryFieldClassified(TaskRequest.class,
                // Fixed tokens, a flag, a number and a date - nothing a person wrote.
                Set.of("priority", "recurrenceType", "nextDueDate", "requiresPhotoProof",
                        "estimatedTimeMinutes", "status"),
                // Free text, an image key, free-form JSON, and two associations compared by
                // internal id - an id must never reach a response.
                Set.of("name", "description", "taskPhotoUrl", "recurrencePattern",
                        "assignedToId", "categoryId"),
                // A task cannot move group or change author, so updateTask ignores both.
                Set.of("groupId", "createdById"));
    }

    @Test
    void groupRequest_everyField_isClassified() {
        assertEveryFieldClassified(GroupRequest.class,
                Set.of(),
                Set.of("name", "description", "photoUid"),
                Set.of());
    }

    @Test
    void userRequest_everyField_isClassified() {
        assertEveryFieldClassified(UserRequest.class,
                Set.of(),
                // A person's own names and picture, on a trail their household can read.
                Set.of("name", "middleName", "lastName", "photoUrl"),
                // Changing the sign-in address is not a profile edit; updateUser ignores it.
                Set.of("email"));
    }

    @Test
    void categoryRequest_everyField_isClassified() {
        assertEveryFieldClassified(CategoryRequest.class,
                Set.of("color", "icon"),
                Set.of("name"),
                Set.of("groupId"));
    }

    @Test
    void tagRequest_everyField_isClassified() {
        assertEveryFieldClassified(TagRequest.class,
                Set.of("color"),
                Set.of("name"),
                Set.of("groupId"));
    }

    private static void assertEveryFieldClassified(Class<?> request, Set<String> valuesShown,
                                                   Set<String> namesOnly, Set<String> untracked) {
        assertThat(Stream.of(valuesShown, namesOnly, untracked).flatMap(Set::stream).toList())
                .as("a %s field must sit in exactly one bucket", request.getSimpleName())
                .doesNotHaveDuplicates();

        assertThat(declaredFields(request))
                .as("every %s field must be classified: values shown, names only, or untracked",
                        request.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(valuesShown, namesOnly, untracked).flatMap(Set::stream).collect(Collectors.toSet()));
    }

    private static Set<String> declaredFields(Class<?> request) {
        return Arrays.stream(request.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
