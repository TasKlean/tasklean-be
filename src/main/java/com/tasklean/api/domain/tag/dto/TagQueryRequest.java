package com.tasklean.api.domain.tag.dto;

import com.tasklean.api.common.validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * What a caller may ask of a household's tag list. Bound from the query string as an object rather
 * than as loose parameters, so a missing household or an over-long search is a 400 from
 * {@code @Valid} — a constraint on a bare {@code @RequestParam} surfaces as a 500 instead.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagQueryRequest {

    @NotBlank
    @Size(min = 1, max = FieldLimits.UID)
    private String groupUid;

    /** Matched case-insensitively anywhere in a tag's name. Omitted, the whole list comes back. */
    @Size(max = FieldLimits.SEARCH_TEXT)
    private String q;
}
