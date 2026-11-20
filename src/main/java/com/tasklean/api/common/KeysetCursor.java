package com.tasklean.api.common;

import com.tasklean.api.common.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Encodes and decodes the opaque cursor a keyset-paged list pages by. The cursor carries the sort
 * key of the last row handed out - a timestamp and a row id - so the next page resumes from there
 * instead of counting rows, which is what keeps paging stable while the table grows at the end it
 * would otherwise count from.
 */
@Component
public class KeysetCursor {

    private static final String SEPARATOR = "|";

    /**
     * Encodes a row's sort key as a cursor. Which timestamp that is belongs to the caller: the
     * audit feed pages by when an entry was written, a task's history by when its instance was due.
     *
     * @param timestamp the leading sort key of the last row on the page just produced
     * @param id        that row's id, the tiebreak
     * @return the cursor the caller passes to resume after it
     */
    public String encode(LocalDateTime timestamp, Long id) {
        String raw = timestamp + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    /**
     * Decodes a cursor back into the position to resume after.
     *
     * @param cursor the cursor from a previous page, or {@code null}/blank for the first page
     * @return the position to read after, or {@code null} to start at the first row
     * @throws InvalidRequestException if the cursor is not one we handed out
     */
    public Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            int split = raw.lastIndexOf(SEPARATOR);
            return new Position(LocalDateTime.parse(raw.substring(0, split)),
                    Long.parseLong(raw.substring(split + 1)));
        } catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException e) {
            // Anything unparseable is refused rather than silently read as the first page: a client
            // retrying a corrupted cursor would otherwise loop over page one forever.
            throw new InvalidRequestException("The page cursor is not valid", ErrorCodes.PAGE_CURSOR_INVALID);
        }
    }

    /** Where a page resumes from: the sort key of the last row the caller already has. */
    public record Position(LocalDateTime timestamp, Long id) {
    }
}
