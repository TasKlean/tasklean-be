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
 * Guards the audit trail against a request field nobody classified. Adding a field to one of these
 * DTOs fails here until it is either tracked by the update or named as deliberately untracked -
 * without that, a new field changes silently and the trail says only that something was edited.
 * The service tests prove the tracked names are the ones actually reported; this proves the list
 * is complete. Role changes take an enum rather than a DTO, so they are not covered here.
 */
class AuditTrackedFieldsTest {

    @Test
    void taskRequest_everyField_isClassified() {
        assertEveryFieldClassified(TaskRequest.class,
                Set.of("name", "description", "taskPhotoUrl", "priority", "recurrenceType",
                        "recurrencePattern", "nextDueDate", "requiresPhotoProof",
                        "estimatedTimeMinutes", "status", "assignedToId", "categoryId"),
                // A task cannot move group or change author, so updateTask ignores both.
                Set.of("groupId", "createdById"));
    }

    @Test
    void groupRequest_everyField_isClassified() {
        assertEveryFieldClassified(GroupRequest.class,
                Set.of("name", "description", "photoUid"),
                Set.of());
    }

    @Test
    void userRequest_everyField_isClassified() {
        assertEveryFieldClassified(UserRequest.class,
                Set.of("name", "middleName", "lastName", "photoUrl"),
                // Changing the sign-in address is not a profile edit; updateUser ignores it.
                Set.of("email"));
    }

    @Test
    void categoryRequest_everyField_isClassified() {
        assertEveryFieldClassified(CategoryRequest.class,
                Set.of("name", "color", "icon"),
                Set.of("groupId"));
    }

    @Test
    void tagRequest_everyField_isClassified() {
        assertEveryFieldClassified(TagRequest.class,
                Set.of("name", "color"),
                Set.of("groupId"));
    }

    private static void assertEveryFieldClassified(Class<?> request, Set<String> tracked, Set<String> untracked) {
        Set<String> classified = Stream.concat(tracked.stream(), untracked.stream()).collect(Collectors.toSet());

        assertThat(declaredFields(request))
                .as("every %s field must be tracked by the update or named as deliberately untracked",
                        request.getSimpleName())
                .containsExactlyInAnyOrderElementsOf(classified);
    }

    private static Set<String> declaredFields(Class<?> request) {
        return Arrays.stream(request.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
