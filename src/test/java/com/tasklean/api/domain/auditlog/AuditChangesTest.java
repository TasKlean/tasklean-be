package com.tasklean.api.domain.auditlog;

import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.task.Task;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditChangesTest {

    private Group group() {
        return Group.builder().name("Home").description(null).build();
    }

    @Test
    void set_aDifferentValue_namesTheFieldAndApplies() {
        Group group = group();

        AuditChanges changes = AuditChanges.create()
                .set("name", group::getName, group::setName, "Away");

        assertThat(changes.fields()).containsExactly("name");
        assertThat(group.getName()).isEqualTo("Away");
    }

    @Test
    void set_theSameValue_namesNothingButStillApplies() {
        Group group = group();

        AuditChanges changes = AuditChanges.create()
                .set("name", group::getName, group::setName, "Home");

        assertThat(changes.fields()).isEmpty();
        // Applying either way keeps the update's stored result exactly as it was before tracking.
        assertThat(group.getName()).isEqualTo("Home");
    }

    @Test
    void set_blankTextAgainstAbsentText_isNotAChange() {
        Group blankOverAbsent = Group.builder().description(null).build();
        Group absentOverBlank = Group.builder().description("   ").build();

        AuditChanges first = AuditChanges.create()
                .set("description", blankOverAbsent::getDescription, blankOverAbsent::setDescription, "");
        AuditChanges second = AuditChanges.create()
                .set("description", absentOverBlank::getDescription, absentOverBlank::setDescription, null);

        // A form that posts an empty box over an empty column has not changed anything.
        assertThat(first.fields()).isEmpty();
        assertThat(second.fields()).isEmpty();
    }

    @Test
    void set_textOverAbsentText_isAChange() {
        Group group = Group.builder().description(null).build();

        AuditChanges changes = AuditChanges.create()
                .set("description", group::getDescription, group::setDescription, "Shared flat");

        assertThat(changes.fields()).containsExactly("description");
    }

    @Test
    void track_valuesThatDiffer_namesTheField() {
        // The ids an association is compared by, since the entity itself is set separately.
        AuditChanges changes = AuditChanges.create().track("assignedToId", 4L, 7L);

        assertThat(changes.fields()).containsExactly("assignedToId");
    }

    @Test
    void track_equalValues_namesNothing() {
        assertThat(AuditChanges.create().track("categoryId", 3L, 3L).fields()).isEmpty();
        assertThat(AuditChanges.create().track("categoryId", null, null).fields()).isEmpty();
    }

    @Test
    void setVisible_aDifferentValue_keepsBothValuesAndApplies() {
        Group group = group();

        AuditChanges changes = AuditChanges.create()
                .setVisible("name", group::getName, group::setName, "Away");

        assertThat(changes.entries()).containsExactly(new AuditFieldChange("name", "Home", "Away"));
        assertThat(group.getName()).isEqualTo("Away");
    }

    @Test
    void set_aDifferentValue_keepsNeitherValue() {
        Group group = group();

        AuditChanges changes = AuditChanges.create()
                .set("name", group::getName, group::setName, "Away");

        // Withholding is the default: a value kept here is kept forever and shown to every reader.
        assertThat(changes.entries()).containsExactly(AuditFieldChange.withheld("name"));
    }

    @Test
    void setVisible_aFieldThatWasEmpty_leavesTheOldValueAbsent() {
        Group group = Group.builder().description(null).build();

        AuditChanges changes = AuditChanges.create()
                .setVisible("description", group::getDescription, group::setDescription, "Shared flat");

        // Absent, not the string "null" - the field had nothing before.
        assertThat(changes.entries()).containsExactly(new AuditFieldChange("description", null, "Shared flat"));
    }

    @Test
    void setVisible_aNonTextValue_keepsItAsText() {
        Task task = Task.builder().estimatedTimeMinutes(15).build();

        AuditChanges changes = AuditChanges.create()
                .setVisible("estimatedTimeMinutes", task::getEstimatedTimeMinutes,
                        task::setEstimatedTimeMinutes, 30);

        assertThat(changes.entries())
                .containsExactly(new AuditFieldChange("estimatedTimeMinutes", "15", "30"));
    }

    @Test
    void track_always_keepsNeitherValue() {
        // An association is compared by internal id, which must never reach a response.
        AuditChanges changes = AuditChanges.create().track("assignedToId", 4L, 7L);

        assertThat(changes.entries()).containsExactly(AuditFieldChange.withheld("assignedToId"));
    }

    @Test
    void isEmpty_onlyWhenNoFieldMoved() {
        Group group = group();

        assertThat(AuditChanges.create().isEmpty()).isTrue();
        assertThat(AuditChanges.create()
                .set("name", group::getName, group::setName, "Away")
                .isEmpty()).isFalse();
    }

    @Test
    void fields_always_keepTheOrderTheUpdateApplied() {
        Group group = Group.builder().name("Home").description("Old").build();

        AuditChanges changes = AuditChanges.create()
                .set("description", group::getDescription, group::setDescription, "New")
                .set("name", group::getName, group::setName, "Away");

        assertThat(changes.fields()).containsExactly("description", "name");
    }

    @Test
    void fields_always_answersACopy() {
        Group group = group();
        AuditChanges changes = AuditChanges.create()
                .set("name", group::getName, group::setName, "Away");

        // The entry is built from this, so a caller must not be able to edit it afterwards.
        assertThatThrownBy(() -> changes.fields().add("description"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
