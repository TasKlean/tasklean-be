package com.tasklean.api.domain.auditlog;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Encodes and decodes the opaque cursor an audit feed pages by. The cursor carries the sort key of
 * the last entry handed out - its timestamp and its row id - so the next page resumes from there
 * instead of counting rows, which is what keeps paging stable while the trail grows at the top.
 */
@Component
class AuditLogCursor {

    private static final String SEPARATOR = "|";

    /**
     * Encodes an entry's sort key as a cursor.
     *
     * @param entry the last entry on the page just produced
     * @return the cursor the caller passes to resume after it
     */
    public String encode(AuditLog entry) {
        String raw = entry.getDateCreated() + SEPARATOR + entry.getIdAuditLog();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    /**
     * Decodes a cursor back into the position to resume after.
     *
     * @param cursor the cursor from a previous page, or {@code null}/blank for the first page
     * @return the position to read after, or {@code null} to start at the newest entry
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

    /** Where a page resumes from: the sort key of the last entry the caller already has. */
    public record Position(LocalDateTime dateCreated, Long id) {
    }
}
