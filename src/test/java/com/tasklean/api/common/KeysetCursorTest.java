package com.tasklean.api.common;

import com.tasklean.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The opaque page cursor. Depends on nothing, so every case is provable without a mock - which is
 * the point of it being its own component rather than a pair of helpers on whichever service pages.
 */
class KeysetCursorTest {

    private final KeysetCursor keysetCursor = new KeysetCursor();

    @Test
    void decode_aCursorThisComponentEncoded_returnsTheSamePosition() {
        LocalDateTime when = LocalDateTime.now(ZoneOffset.UTC);

        KeysetCursor.Position position = keysetCursor.decode(keysetCursor.encode(when, 42L));

        assertThat(position.timestamp()).isEqualTo(when);
        assertThat(position.id()).isEqualTo(42L);
    }

    @Test
    void encode_always_hidesTheSortKeyItCarries() {
        // It is handed to a client, so it must not read as a row id to tamper with.
        String cursor = keysetCursor.encode(LocalDateTime.of(2026, 11, 6, 12, 0), 42L);

        assertThat(cursor).doesNotContain("42").doesNotContain("2026");
    }

    @Test
    void decode_noCursor_startsAtTheFirstRow() {
        assertThat(keysetCursor.decode(null)).isNull();
        assertThat(keysetCursor.decode("  ")).isNull();
    }

    @Test
    void decode_cursorThatIsNotBase64_isRefused() {
        assertThatThrownBy(() -> keysetCursor.decode("not a cursor!!"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(e -> ((InvalidRequestException) e).getCode())
                .isEqualTo(ErrorCodes.PAGE_CURSOR_INVALID);
    }

    @Test
    void decode_tamperedCursor_isRefusedRatherThanReadAsTheFirstPage() {
        // Silently starting over would loop a retrying client across page one forever.
        String tampered = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-11-06T12:00|not-a-number".getBytes());

        assertThatThrownBy(() -> keysetCursor.decode(tampered))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void decode_cursorMissingItsRowId_isRefused() {
        String halfACursor = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-11-06T12:00".getBytes());

        assertThatThrownBy(() -> keysetCursor.decode(halfACursor))
                .isInstanceOf(InvalidRequestException.class);
    }
}
