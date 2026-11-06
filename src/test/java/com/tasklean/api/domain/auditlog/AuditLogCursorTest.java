package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The opaque page cursor. Depends on nothing, so every case is provable without a mock - which is
 * the point of it being its own component rather than a pair of helpers on the service.
 */
class AuditLogCursorTest {

    private final AuditLogCursor auditLogCursor = new AuditLogCursor();

    private AuditLog entry(LocalDateTime dateCreated, Long id) {
        return AuditLog.builder().dateCreated(dateCreated).idAuditLog(id).build();
    }

    @Test
    void decode_aCursorThisComponentEncoded_returnsTheSamePosition() {
        LocalDateTime when = LocalDateTime.now(ZoneOffset.UTC);

        AuditLogCursor.Position position = auditLogCursor.decode(auditLogCursor.encode(entry(when, 42L)));

        assertThat(position.dateCreated()).isEqualTo(when);
        assertThat(position.id()).isEqualTo(42L);
    }

    @Test
    void encode_always_hidesTheSortKeyItCarries() {
        // It is handed to a client, so it must not read as a row id to tamper with.
        String cursor = auditLogCursor.encode(entry(LocalDateTime.of(2026, 11, 6, 12, 0), 42L));

        assertThat(cursor).doesNotContain("42").doesNotContain("2026");
    }

    @Test
    void decode_noCursor_startsAtTheNewestEntry() {
        assertThat(auditLogCursor.decode(null)).isNull();
        assertThat(auditLogCursor.decode("  ")).isNull();
    }

    @Test
    void decode_cursorThatIsNotBase64_isRefused() {
        assertThatThrownBy(() -> auditLogCursor.decode("not a cursor!!"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).getCode())
                .isEqualTo(ErrorCodes.PAGE_CURSOR_INVALID);
    }

    @Test
    void decode_tamperedCursor_isRefusedRatherThanReadAsTheFirstPage() {
        // Silently starting over would loop a retrying client across page one forever.
        String tampered = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-11-06T12:00|not-a-number".getBytes());

        assertThatThrownBy(() -> auditLogCursor.decode(tampered))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void decode_cursorMissingItsRowId_isRefused() {
        String halfACursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-11-06T12:00".getBytes());

        assertThatThrownBy(() -> auditLogCursor.decode(halfACursor))
                .isInstanceOf(InvalidRequestException.class);
    }
}
