package com.tasklean.api.domain.group.dto;

import com.tasklean.api.common.validation.FieldLimits;
import com.tasklean.api.domain.group.GroupSort;
import com.tasklean.api.domain.groupmember.GroupRole;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * What a caller may ask of their own group list. Bound from the query string as an object rather
 * than as loose parameters, so {@code @Valid} rejects an over-long search as a 400 - a constraint
 * on a bare {@code @RequestParam} would surface as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyGroupFilterRequest {

    /** Matched case-insensitively against a group's name and its description. */
    @Size(max = FieldLimits.SEARCH_TEXT)
    private String q;

    /** The roles to keep, repeatable. Naming neither role, or naming both, is the whole list. */
    private List<GroupRole> role;

    /** The ordering to apply. Omitted, the list comes back ordered by name, A to Z. */
    private GroupSort sort = GroupSort.NAME_ASC;
}
