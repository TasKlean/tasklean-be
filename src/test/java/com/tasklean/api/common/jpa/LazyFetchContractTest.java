package com.tasklean.api.common.jpa;

import com.tasklean.api.domain.category.CategoryRepository;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.tag.TagRepository;
import com.tasklean.api.domain.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that every finder whose rows are mapped to a DTO reading non-id fields of an association
 * declares that association in its {@link EntityGraph}. With {@code spring.jpa.open-in-view=false}
 * a missing path is a 500 at runtime, and no mocked-repository test can catch it: a mock hands back
 * a fully built object, never a proxy.
 */
class LazyFetchContractTest {

    @Test
    void myGroupsFinder_fetchesTheGroup() throws Exception {
        // MyGroupResponse reads the group's name, description and invite code, not its id.
        assertThat(pathsOf(GroupMemberRepository.class, "findByUserIdUserAndIsActiveTrue", Long.class))
                .contains("group");
    }

    @Test
    void memberListFinder_fetchesTheUserBehindEveryAttribution() throws Exception {
        assertThat(pathsOf(GroupMemberRepository.class, "findByGroupUidAndIsActiveTrue", String.class))
                .contains("user", "addedBy.user", "removedBy.user");
    }

    @Test
    void singleMemberFinder_fetchesTheUserBehindEveryAttribution() throws Exception {
        assertThat(pathsOf(GroupMemberRepository.class, "findDetailedByUid", String.class))
                .contains("user", "addedBy.user", "removedBy.user");
    }

    @Test
    void taskFinder_fetchesTheHouseholdAndThePeople() throws Exception {
        // TaskResponse reads the group's uid and the names behind both memberships.
        assertThat(pathsOf(TaskRepository.class, "findDetailedByUidAndIsActiveTrue", String.class))
                .contains("group", "createdBy.user", "assignedTo.user");
    }

    @Test
    void labelFinders_fetchTheHousehold() throws Exception {
        // Both responses name the household by uid, which a proxy cannot serve.
        assertThat(pathsOf(CategoryRepository.class, "findByIdCategoryAndIsActiveTrue", Long.class))
                .contains("group");
        assertThat(pathsOf(CategoryRepository.class,
                "findByGroupIdGroupAndIsActiveTrueOrderByName", Long.class)).contains("group");
        assertThat(pathsOf(TagRepository.class, "findByIdTagAndIsActiveTrue", Long.class))
                .contains("group");
        assertThat(pathsOf(TagRepository.class, "searchActive", Long.class, String.class))
                .contains("group");
    }

    @Test
    void taskAuthorizationFinder_staysLean() throws Exception {
        // TaskSecurity reads the group id and the author's membership id, both served by proxies.
        Method method = TaskRepository.class.getMethod("findByUid", String.class);

        assertThat(method.getAnnotation(EntityGraph.class)).isNull();
    }

    @Test
    void authorizationFinder_staysLean() throws Exception {
        // The counterpart rule: GroupSecurity reads ids only, so it must not pay for those joins.
        Method method = GroupMemberRepository.class.getMethod("findByUid", String.class);

        assertThat(method.getAnnotation(EntityGraph.class)).isNull();
    }

    private String[] pathsOf(Class<?> repository, String method, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        EntityGraph graph = repository.getMethod(method, parameterTypes).getAnnotation(EntityGraph.class);
        assertThat(graph).as("%s is missing @EntityGraph", method).isNotNull();
        return graph.attributePaths();
    }
}
