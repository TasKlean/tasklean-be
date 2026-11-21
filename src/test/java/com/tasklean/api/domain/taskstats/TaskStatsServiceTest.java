package com.tasklean.api.domain.taskstats;

import com.tasklean.api.common.exception.InvalidRequestException;
import com.tasklean.api.common.exception.ResourceNotFoundException;
import com.tasklean.api.domain.group.Group;
import com.tasklean.api.domain.group.GroupRepository;
import com.tasklean.api.domain.groupmember.GroupMember;
import com.tasklean.api.domain.groupmember.GroupMemberRepository;
import com.tasklean.api.domain.taskcompletion.TaskCompletionRepository;
import com.tasklean.api.domain.taskcompletion.TaskCompletionRepository.MemberMinutes;
import com.tasklean.api.domain.taskstats.dto.MemberShareResponse;
import com.tasklean.api.domain.taskstats.dto.TaskStatsQueryRequest;
import com.tasklean.api.domain.taskstats.dto.TaskStatsResponse;
import com.tasklean.api.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStatsServiceTest {

    private static final String GROUP_UID = "grp-1";
    private static final Long GROUP_ID = 1L;

    @Mock
    private TaskCompletionRepository taskCompletionRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupRepository groupRepository;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private TaskStatsService taskStatsService;

    // ----------------------------------------------------------------- the roster is what shows

    @Test
    void getStats_memberWhoDidNothing_isListedAtZeroRatherThanDropped() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"), member(11L, "gm-ann", "Ann"));
        aggregateReturns(minutes(10L, 90, 3));

        TaskStatsResponse stats = taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest());

        // A quiet member vanishing would read as if they were not in the household at all.
        assertThat(stats.getMembers()).extracting(r -> r.getMember().getMemberUid())
                .containsExactly("gm-bob", "gm-ann");
        assertThat(stats.getMembers().getLast().getMinutes()).isZero();
        assertThat(stats.getMembers().getLast().getRoundsDone()).isZero();
    }

    @Test
    void getStats_always_sortsBusiestFirstAndTotalsTheRows() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"), member(11L, "gm-ann", "Ann"));
        aggregateReturns(minutes(10L, 30, 1), minutes(11L, 120, 4));

        TaskStatsResponse stats = taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest());

        assertThat(stats.getMembers()).extracting(r -> r.getMember().getMemberUid())
                .containsExactly("gm-ann", "gm-bob");
        assertThat(stats.getTotalMinutes()).isEqualTo(150);
        assertThat(stats.getTotalRounds()).isEqualTo(5);
    }

    @Test
    void getStats_formerMemberWithWorkInTheWindow_isListedSoTheRowsStillSumToTheTotal() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"));
        formerRosterIs(member(99L, "gm-gone", "Dana"));
        aggregateReturns(minutes(10L, 20, 1), minutes(99L, 60, 2));

        TaskStatsResponse stats = taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest());

        // Their work happened. Dropping it would leave the total disagreeing with its own rows.
        assertThat(stats.getMembers()).extracting(r -> r.getMember().getMemberUid())
                .containsExactly("gm-gone", "gm-bob");
        assertThat(stats.getMembers().getFirst().isFormer()).isTrue();
        assertThat(stats.getTotalMinutes()).isEqualTo(80);
    }

    @Test
    void getStats_noWorkByAnyFormerMember_doesNotEvenAskForThem() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"));
        aggregateReturns(minutes(10L, 20, 1));

        taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest());

        // A former member is never listed at zero, so with nothing left over there is nothing to ask.
        verify(groupMemberRepository, never()).findByGroupUidAndIsActiveFalse(anyString());
    }

    // ----------------------------------------------------------------- the window

    @Test
    void getStats_noRangeGiven_usesTheCurrentUtcWeekMondayToMonday() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"));
        aggregateReturns();

        TaskStatsResponse stats = taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest());

        assertThat(stats.getFrom().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(stats.getFrom().toLocalTime()).isEqualTo(java.time.LocalTime.MIDNIGHT);
        assertThat(stats.getTo()).isEqualTo(stats.getFrom().plusWeeks(1));
    }

    @Test
    void getStats_rangeWithAnOffset_isConvertedToUtcBeforeItIsQueried() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"));
        aggregateReturns();
        TaskStatsQueryRequest query = new TaskStatsQueryRequest();
        query.setDateFrom(OffsetDateTime.parse("2026-11-16T01:00:00+02:00"));
        query.setDateTo(OffsetDateTime.parse("2026-11-23T01:00:00+02:00"));

        taskStatsService.getStats(GROUP_UID, query);

        // The column stores an instant with no zone, so the caller's offset is applied here - a
        // naive value would shift the window by their own offset and report nothing wrong.
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskCompletionRepository).sumMinutesPerMember(anyLong(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(LocalDateTime.parse("2026-11-15T23:00:00"));
        assertThat(to.getValue()).isEqualTo(LocalDateTime.parse("2026-11-22T23:00:00"));
    }

    @Test
    void getStats_onlyAStartGiven_leavesTheOtherEndOpen() {
        groupExists();
        rosterIs(member(10L, "gm-bob", "Bob"));
        aggregateReturns();
        TaskStatsQueryRequest query = new TaskStatsQueryRequest();
        query.setDateFrom(OffsetDateTime.parse("2026-11-16T00:00:00Z"));

        TaskStatsResponse stats = taskStatsService.getStats(GROUP_UID, query);

        assertThat(stats.getFrom()).isEqualTo(LocalDateTime.parse("2026-11-16T00:00:00"));
        assertThat(stats.getTo()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void getStats_windowEndingWhereItStarts_isRefused() {
        groupExists();
        TaskStatsQueryRequest query = new TaskStatsQueryRequest();
        query.setDateFrom(OffsetDateTime.parse("2026-11-16T00:00:00Z"));
        query.setDateTo(OffsetDateTime.parse("2026-11-16T00:00:00Z"));

        // Half-open, so an empty window is a mistake rather than a window that matches one instant.
        assertThatThrownBy(() -> taskStatsService.getStats(GROUP_UID, query))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must come after");
        verify(taskCompletionRepository, never()).sumMinutesPerMember(anyLong(), any(), any());
    }

    @Test
    void getStats_deletedHousehold_isNotFoundBeforeAnythingIsCounted() {
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskStatsService.getStats(GROUP_UID, new TaskStatsQueryRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskCompletionRepository, never()).sumMinutesPerMember(anyLong(), any(), any());
    }

    // ----------------------------------------------------------------- fixtures

    private void groupExists() {
        when(groupRepository.findByUidAndIsActiveTrue(GROUP_UID))
                .thenReturn(Optional.of(Group.builder().idGroup(GROUP_ID).uid(GROUP_UID).build()));
    }

    private void rosterIs(GroupMember... members) {
        when(groupMemberRepository.findByGroupUidAndIsActiveTrue(GROUP_UID))
                .thenReturn(List.of(members));
    }

    private void formerRosterIs(GroupMember... members) {
        when(groupMemberRepository.findByGroupUidAndIsActiveFalse(GROUP_UID))
                .thenReturn(List.of(members));
    }

    private void aggregateReturns(MemberMinutes... rows) {
        when(taskCompletionRepository.sumMinutesPerMember(anyLong(), any(), any()))
                .thenReturn(List.of(rows));
    }

    private GroupMember member(Long id, String uid, String name) {
        return GroupMember.builder()
                .idGroupMember(id).uid(uid)
                .user(User.builder().uid("usr-" + id).name(name).lastName("X").build())
                .build();
    }

    private MemberMinutes minutes(Long memberId, long minutes, long rounds) {
        return new MemberMinutes() {
            @Override
            public Long getMemberId() {
                return memberId;
            }

            @Override
            public long getMinutes() {
                return minutes;
            }

            @Override
            public long getRounds() {
                return rounds;
            }
        };
    }
}
