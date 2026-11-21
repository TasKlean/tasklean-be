package com.tasklean.api.domain.taskstats;

import com.tasklean.api.common.ErrorMessages;
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
import com.tasklean.api.domain.groupmember.dto.MemberSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Who did how much of the housework over a window — the share. Reads the recorded rounds and
 * attributes their minutes to the member each is credited to.
 */
@Service
@RequiredArgsConstructor
public class TaskStatsService {

    private final TaskCompletionRepository taskCompletionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final Clock clock;

    /**
     * Totals each member's work in a household over a window.
     *
     * <p>The rows are the active roster plus any former member who did something in the window, so
     * the totals equal the sum of what is listed. Busiest first.
     *
     * @param groupUid the household
     * @param query    the window, or empty for the current UTC week
     * @return one row per member, with the window that was actually used
     * @throws ResourceNotFoundException if the household does not exist or has been deleted
     * @throws InvalidRequestException   if the window ends before it starts
     */
    public TaskStatsResponse getStats(String groupUid, TaskStatsQueryRequest query) {
        Group group = groupRepository.findByUidAndIsActiveTrue(groupUid)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.GROUP_NOT_FOUND));

        Window window = window(query);
        Map<Long, MemberMinutes> done = taskCompletionRepository
                .sumMinutesPerMember(group.getIdGroup(), window.from(), window.to()).stream()
                .collect(Collectors.toMap(MemberMinutes::getMemberId, Function.identity()));

        List<MemberShareResponse> rows = rows(groupUid, done);
        return TaskStatsResponse.builder()
                .groupUid(groupUid)
                .from(window.from())
                .to(window.to())
                .members(rows)
                .totalMinutes(rows.stream().mapToLong(MemberShareResponse::getMinutes).sum())
                .totalRounds(rows.stream().mapToLong(MemberShareResponse::getRoundsDone).sum())
                .build();
    }

    // The active roster is what puts a member who did nothing on the share at zero - the aggregate
    // only knows the ones who did something. A former member is added only when they did, so a
    // household's past weeks keep adding up after somebody leaves.
    private List<MemberShareResponse> rows(String groupUid, Map<Long, MemberMinutes> done) {
        List<MemberShareResponse> rows = new ArrayList<>();
        for (GroupMember member : groupMemberRepository.findByGroupUidAndIsActiveTrue(groupUid)) {
            rows.add(row(member, done.remove(member.getIdGroupMember()), false));
        }
        // Whatever is left is credited to memberships that have ended.
        if (!done.isEmpty()) {
            for (GroupMember member : groupMemberRepository.findByGroupUidAndIsActiveFalse(groupUid)) {
                MemberMinutes theirs = done.get(member.getIdGroupMember());
                if (theirs != null) {
                    rows.add(row(member, theirs, true));
                }
            }
        }
        rows.sort(Comparator.comparingLong(MemberShareResponse::getMinutes).reversed()
                .thenComparing(r -> r.getMember().getMemberUid()));
        return rows;
    }

    private MemberShareResponse row(GroupMember member, MemberMinutes totals, boolean former) {
        return MemberShareResponse.builder()
                .member(MemberSummaryResponse.from(member))
                .minutes(totals == null ? 0 : totals.getMinutes())
                .roundsDone(totals == null ? 0 : totals.getRounds())
                .former(former)
                .build();
    }

    // Half-open, so adjacent windows cannot both claim a round recorded on the boundary.
    private Window window(TaskStatsQueryRequest query) {
        LocalDateTime from = utc(query.getDateFrom());
        LocalDateTime to = utc(query.getDateTo());
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidRequestException("dateTo must come after dateFrom");
        }
        if (from == null && to == null) {
            return currentWeek();
        }
        // One bound given: the other opens, so "everything since Monday" needs no second parameter.
        return new Window(from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from,
                to == null ? LocalDateTime.now(clock).plusDays(1) : to);
    }

    // Monday to Monday in UTC. A naive default cannot know the caller's offset, so a household that
    // wants its own local week sends the range instead of relying on this.
    private Window currentWeek() {
        LocalDateTime monday = LocalDateTime.now(clock)
                .with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay();
        return new Window(monday, monday.plusWeeks(1));
    }

    // The column holds UTC with no zone of its own, so the caller's offset is applied here rather
    // than compared against it - the request states an instant, the column stores one.
    private LocalDateTime utc(OffsetDateTime value) {
        return value != null ? value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() : null;
    }

    private record Window(LocalDateTime from, LocalDateTime to) {
    }
}
