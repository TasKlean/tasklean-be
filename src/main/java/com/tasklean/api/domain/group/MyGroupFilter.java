package com.tasklean.api.domain.group;

import com.tasklean.api.domain.group.dto.MyGroupFilterRequest;
import com.tasklean.api.domain.group.dto.MyGroupResponse;
import com.tasklean.api.domain.groupmember.GroupRole;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Narrowing and ordering the caller's own group list. Its own component rather than service code
 * because it depends on nothing: the list is already loaded and counted by the time it arrives, so
 * every ordering and every narrowing is provable without a mock.
 */
@Component
public class MyGroupFilter {

    // Stands in for a date the row does not carry, so such a group sorts last under every
    // ordering here rather than ahead of real ones.
    private static final LocalDateTime UNDATED = LocalDateTime.MIN;

    /**
     * Applies a filter to the caller's groups.
     *
     * @param groups the caller's groups, each already carrying its member count
     * @param filter what the request asked for
     * @return a new list, narrowed and ordered; the caller's list is left as it was
     */
    public List<MyGroupResponse> apply(List<MyGroupResponse> groups, MyGroupFilterRequest filter) {
        String needle = needle(filter.getQ());
        Set<GroupRole> roles = roles(filter.getRole());

        return groups.stream()
                .filter(group -> roles.contains(group.getMyRole()) && matches(group, needle))
                .sorted(comparator(filter.getSort()))
                .toList();
    }

    private Comparator<MyGroupResponse> comparator(GroupSort sort) {
        // A fresh Collator per call: comparing with one is not thread-safe, and one sort runs on
        // one thread. PRIMARY ignores case and accents, which is the frontend's own name compare.
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        collator.setStrength(Collator.PRIMARY);

        Comparator<MyGroupResponse> byName =
                Comparator.comparing(group -> group.getName() == null ? "" : group.getName(), collator);
        Comparator<MyGroupResponse> byJoined = Comparator.comparing(MyGroupFilter::joined);
        Comparator<MyGroupResponse> byCreated = Comparator.comparing(MyGroupFilter::created);
        Comparator<MyGroupResponse> byMembers = Comparator.comparingInt(MyGroupFilter::members);

        // An absent sort parameter is the field's default, but "sort=" with nothing after it binds
        // to null, so the default lives here too rather than only on the request.
        return switch (sort == null ? GroupSort.NAME_ASC : sort) {
            case NAME_ASC -> byName;
            case NAME_DESC -> byName.reversed();
            case JOINED_DESC -> byJoined.reversed().thenComparing(byName);
            case CREATED_DESC -> byCreated.reversed().thenComparing(byName);
            case MEMBERS_DESC -> byMembers.reversed().thenComparing(byName);
        };
    }

    private Set<GroupRole> roles(List<GroupRole> asked) {
        if (asked == null) {
            return EnumSet.allOf(GroupRole.class);
        }

        Set<GroupRole> kept = asked.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(GroupRole.class)));
        // Asking for no role at all is asking for no narrowing, the same as asking for every role.
        return kept.isEmpty() ? EnumSet.allOf(GroupRole.class) : kept;
    }

    private String needle(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    // The two fields are matched separately rather than as one joined string, so a needle that
    // straddles the end of the name and the start of the description matches nothing.
    private boolean matches(MyGroupResponse group, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return holds(group.getName(), needle) || holds(group.getDescription(), needle);
    }

    private boolean holds(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static LocalDateTime joined(MyGroupResponse group) {
        return group.getDateJoined() == null ? UNDATED : group.getDateJoined();
    }

    private static LocalDateTime created(MyGroupResponse group) {
        return group.getDateCreated() == null ? UNDATED : group.getDateCreated();
    }

    private static int members(MyGroupResponse group) {
        return group.getMemberCount() == null ? 0 : group.getMemberCount();
    }
}
