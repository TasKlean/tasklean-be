package com.tasklean.api.common.jpa;

import com.tasklean.api.domain.auditlog.AuditLogRepository;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;

import java.time.LocalDateTime;

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
    void auditFinders_fetchTheActor() throws Exception {
        // AuditLogResponse renders the actor's name, so every audit read needs them loaded.
        assertThat(pathsOf(AuditLogRepository.class, "findInGroup",
                Long.class, LocalDateTime.class, Long.class, Limit.class)).contains("actorUser");
        assertThat(pathsOf(AuditLogRepository.class, "findForEntity",
                String.class, Long.class, LocalDateTime.class, Long.class, Limit.class)).contains("actorUser");
        assertThat(pathsOf(AuditLogRepository.class, "findInGroupVisibleToMember", Long.class, Long.class,
                String.class, LocalDateTime.class, Long.class, Limit.class)).contains("actorUser");
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
