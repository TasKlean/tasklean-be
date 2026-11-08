package com.tasklean.api.domain.auditlog;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One field an update touched. {@code from} and {@code to} are present only for a field whose value
 * is safe to keep - most carry the name alone. The same shape is stored in {@code changed_fields}
 * and returned to the client, so one Jackson annotation keeps both compact.
 *
 * @param field the field's name as the request DTO spells it
 * @param from  its value before the update, or null when withheld or previously empty
 * @param to    its value after the update, or null when withheld or now empty
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditFieldChange(String field, String from, String to) {

    /**
     * Names a field without disclosing what it held.
     *
     * @param field the field's name
     * @return the change, with no values
     */
    public static AuditFieldChange withheld(String field) {
        return new AuditFieldChange(field, null, null);
    }

    /**
     * Names a field and keeps what it moved between. Only for a value that is safe to persist in an
     * append-only trail and to show every reader of it - never text a person wrote, never a secret.
     *
     * @param field    the field's name
     * @param oldValue its value before the update
     * @param newValue its value after it
     * @return the change, carrying both values as text
     */
    public static AuditFieldChange shown(String field, Object oldValue, Object newValue) {
        return new AuditFieldChange(field, text(oldValue), text(newValue));
    }

    // An absent value stays absent rather than becoming the string "null".
    private static String text(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
