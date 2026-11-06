package com.tasklean.api.domain.auditlog.dto;

import lombok.*;

/** What an audit entry is about: the entity's type, and the identifier the API addresses it by. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEntityResponse {

    private String type;
    private String ref;
}
