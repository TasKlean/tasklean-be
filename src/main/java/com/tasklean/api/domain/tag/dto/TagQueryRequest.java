package com.tasklean.api.domain.tag.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * How a caller may narrow a household's tag list. The household itself is not here: it is the
 * resource being listed, so it names itself in the path. Bound as an object rather than a loose
 * parameter, so an over-long search is a 400 from {@code @Valid} - a constraint on a bare
 * {@code @RequestParam} surfaces as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagQueryRequest {

    /** Matched case-insensitively anywhere in a tag's name. Omitted, the whole list comes back. */
    @Size(max = FieldLimits.SEARCH_TEXT)
    private String q;
}
