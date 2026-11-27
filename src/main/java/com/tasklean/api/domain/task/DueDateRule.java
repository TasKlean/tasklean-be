package com.tasklean.api.domain.task;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Whether a due date a caller sent may be stored. A chore is scheduled forwards, so a new one
 * cannot already be late - but an edit carries the whole task, so it must not refuse a chore that
 * is.
 *
 * <p>Judged by calendar date in the caller's own offset, which is why the field carries one:
 * comparing instants would call this evening's due date past, and comparing in UTC would call the
 * caller's own today yesterday for everybody west of it.
 */
@Component
@RequiredArgsConstructor
public class DueDateRule {

    private final Clock clock;

    /**
     * Refuses a due date that has already gone by, for a chore being created.
     *
     * @param requested the due date the caller sent, or null if they sent none
     * @throws InvalidRequestException if it falls before the caller's today
     */
    public void requireNotPast(OffsetDateTime requested) {
        if (requested != null && requested.toLocalDate().isBefore(todayFor(requested))) {
            throw new InvalidRequestException("A due date cannot be in the past",
                    ErrorCodes.TASK_DUE_DATE_PAST);
        }
    }

    /**
     * Refuses a due date being moved into the past, for a chore being edited. One the chore
     * already has passes through untouched: an edit sends the whole task, so refusing it would
     * leave every overdue chore unable to be renamed or reassigned - and a round written off as
     * missed keeps its anchor parked in the past by design.
     *
     * @param requested the due date the caller sent, or null if they sent none
     * @param stored    the due date the chore currently has, or null if it has none
     * @throws InvalidRequestException if it falls before the caller's today and is not the date the
     *                                 chore already had
     */
    public void requireNotMovedIntoPast(OffsetDateTime requested, LocalDateTime stored) {
        // Both dates in the caller's offset, never a stored UTC date against a local one: those
        // disagree across midnight, so a chore's own date would read as a move and be refused.
        if (requested != null && stored != null
                && requested.toLocalDate().equals(dateFor(stored, requested))) {
            return;
        }
        requireNotPast(requested);
    }

    // What day the stored value falls on where the caller is. Stored naive UTC, so a late local
    // time sits on the next UTC day - and an early one on the previous.
    private LocalDate dateFor(LocalDateTime storedUtc, OffsetDateTime requested) {
        return storedUtc.atOffset(ZoneOffset.UTC)
                .withOffsetSameInstant(requested.getOffset())
                .toLocalDate();
    }

    // The caller's own day, not the server's: the offset they sent is what says which day it is
    // where they are.
    private LocalDate todayFor(OffsetDateTime requested) {
        return OffsetDateTime.now(clock).withOffsetSameInstant(requested.getOffset()).toLocalDate();
    }
}
