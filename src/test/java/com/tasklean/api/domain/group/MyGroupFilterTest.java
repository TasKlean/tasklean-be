package com.tasklean.api.domain.group;

import com.tasklean.api.domain.group.dto.MyGroupFilterRequest;
import com.tasklean.api.domain.group.dto.MyGroupResponse;
import com.tasklean.api.domain.groupmember.GroupRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyGroupFilterTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC);

    private final MyGroupFilter filter = new MyGroupFilter();

    @Test
    void apply_defaultSort_ordersByNameIgnoringCaseAndAccents() {
        List<MyGroupResponse> groups = List.of(
                named("zebra"), named("Élan"), named("apple"), named("Bruno"));

        assertThat(names(filter.apply(groups, request()))).containsExactly("apple", "Bruno", "Élan", "zebra");
    }

    @Test
    void apply_nameDesc_reversesTheNameOrder() {
        List<MyGroupResponse> groups = List.of(named("apple"), named("zebra"), named("Bruno"));

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.NAME_DESC);

        assertThat(names(filter.apply(groups, request))).containsExactly("zebra", "Bruno", "apple");
    }

    @Test
    void apply_nullSort_ordersByName() {
        List<MyGroupResponse> groups = List.of(named("zebra"), named("apple"));

        MyGroupFilterRequest request = request();
        request.setSort(null);

        assertThat(names(filter.apply(groups, request))).containsExactly("apple", "zebra");
    }

    @Test
    void apply_joinedDesc_putsTheNewestMembershipFirst() {
        MyGroupResponse old = named("apple");
        old.setDateJoined(NOW.minusDays(10));
        MyGroupResponse recent = named("zebra");
        recent.setDateJoined(NOW);

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.JOINED_DESC);

        assertThat(names(filter.apply(List.of(old, recent), request))).containsExactly("zebra", "apple");
    }

    @Test
    void apply_createdDesc_putsTheNewestGroupFirst() {
        MyGroupResponse old = named("apple");
        old.setDateCreated(NOW.minusDays(10));
        MyGroupResponse recent = named("zebra");
        recent.setDateCreated(NOW);

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.CREATED_DESC);

        assertThat(names(filter.apply(List.of(old, recent), request))).containsExactly("zebra", "apple");
    }

    @Test
    void apply_membersDesc_putsTheBiggestGroupFirst() {
        MyGroupResponse small = named("apple");
        small.setMemberCount(2);
        MyGroupResponse big = named("zebra");
        big.setMemberCount(9);

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.MEMBERS_DESC);

        assertThat(names(filter.apply(List.of(small, big), request))).containsExactly("zebra", "apple");
    }

    @Test
    void apply_sameDate_fallsBackToName() {
        MyGroupResponse second = named("zebra");
        second.setDateJoined(NOW);
        MyGroupResponse first = named("apple");
        first.setDateJoined(NOW);

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.JOINED_DESC);

        assertThat(names(filter.apply(List.of(second, first), request))).containsExactly("apple", "zebra");
    }

    @Test
    void apply_missingDate_sortsLast() {
        MyGroupResponse undated = named("apple");
        MyGroupResponse dated = named("zebra");
        dated.setDateJoined(NOW.minusYears(5));

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.JOINED_DESC);

        assertThat(names(filter.apply(List.of(undated, dated), request))).containsExactly("zebra", "apple");
    }

    @Test
    void apply_missingMemberCount_sortsLast() {
        MyGroupResponse uncounted = named("apple");
        MyGroupResponse counted = named("zebra");
        counted.setMemberCount(1);

        MyGroupFilterRequest request = request();
        request.setSort(GroupSort.MEMBERS_DESC);

        assertThat(names(filter.apply(List.of(uncounted, counted), request))).containsExactly("zebra", "apple");
    }

    @Test
    void apply_query_matchesNameOrDescriptionIgnoringCase() {
        MyGroupResponse byName = named("Kitchen Crew");
        MyGroupResponse byDescription = named("apple");
        byDescription.setDescription("Our KITCHEN rota");
        MyGroupResponse neither = named("Garden");

        MyGroupFilterRequest request = request();
        request.setQ("kitchen");

        assertThat(names(filter.apply(List.of(byName, byDescription, neither), request)))
                .containsExactly("apple", "Kitchen Crew");
    }

    @Test
    void apply_query_isTrimmedBeforeMatching() {
        MyGroupFilterRequest request = request();
        request.setQ("  kitchen  ");

        assertThat(filter.apply(List.of(named("Kitchen Crew")), request)).hasSize(1);
    }

    @Test
    void apply_query_doesNotMatchAcrossNameAndDescription() {
        MyGroupResponse group = named("kitchen");
        group.setDescription("crew");

        MyGroupFilterRequest request = request();
        request.setQ("kitchen crew");

        assertThat(filter.apply(List.of(group), request)).isEmpty();
    }

    @Test
    void apply_blankQuery_keepsEveryGroup() {
        MyGroupFilterRequest request = request();
        request.setQ("   ");

        assertThat(filter.apply(List.of(named("apple"), named("zebra")), request)).hasSize(2);
    }

    @Test
    void apply_oneRole_keepsOnlyThatRole() {
        MyGroupResponse managed = named("apple");
        managed.setMyRole(GroupRole.GROUP_ADMIN);
        MyGroupResponse joined = named("zebra");

        MyGroupFilterRequest request = request();
        request.setRole(List.of(GroupRole.GROUP_ADMIN));

        assertThat(names(filter.apply(List.of(managed, joined), request))).containsExactly("apple");
    }

    @Test
    void apply_bothRoles_keepsEveryGroup() {
        MyGroupResponse managed = named("apple");
        managed.setMyRole(GroupRole.GROUP_ADMIN);

        MyGroupFilterRequest request = request();
        request.setRole(List.of(GroupRole.GROUP_ADMIN, GroupRole.GROUP_MEMBER));

        assertThat(filter.apply(List.of(managed, named("zebra")), request)).hasSize(2);
    }

    @Test
    void apply_emptyRoleList_keepsEveryGroup() {
        MyGroupFilterRequest request = request();
        request.setRole(List.of());

        assertThat(filter.apply(List.of(named("apple"), named("zebra")), request)).hasSize(2);
    }

    @Test
    void apply_leavesTheCallersListAsItWas() {
        List<MyGroupResponse> groups = new ArrayList<>(List.of(named("zebra"), named("apple")));

        filter.apply(groups, request());

        assertThat(names(groups)).containsExactly("zebra", "apple");
    }

    private static MyGroupFilterRequest request() {
        return new MyGroupFilterRequest();
    }

    private static MyGroupResponse named(String name) {
        return MyGroupResponse.builder().name(name).myRole(GroupRole.GROUP_MEMBER).build();
    }

    private static List<String> names(List<MyGroupResponse> groups) {
        return groups.stream().map(MyGroupResponse::getName).toList();
    }
}
