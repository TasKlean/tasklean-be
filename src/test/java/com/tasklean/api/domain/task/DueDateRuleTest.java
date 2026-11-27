package com.tasklean.api.domain.task;

import com.tasklean.api.common.ErrorCodes;
import com.tasklean.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueDateRuleTest {

    // Midday, so "today" has hours either side of it: a rule comparing instants rather than dates
    // would pass the morning cases and fail the afternoon ones.
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private final DueDateRule rule = new DueDateRule(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void requireNotPast_yesterday_isRefused() {
        assertThatThrownBy(() -> rule.requireNotPast(utc("2026-11-24T23:59:00")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot be in the past")
                .extracting(e -> ((InvalidRequestException) e).getCode())
                .isEqualTo(ErrorCodes.TASK_DUE_DATE_PAST);
    }

    @Test
    void requireNotPast_earlierToday_isAllowed() {
        // By date, not by instant: a chore due today carries whatever time of day the form
        // produced, so an instant comparison would refuse its own due date all afternoon.
        assertThatCode(() -> rule.requireNotPast(utc("2026-11-25T08:00:00"))).doesNotThrowAnyException();
    }

    @Test
    void requireNotPast_tomorrow_isAllowed() {
        assertThatCode(() -> rule.requireNotPast(utc("2026-11-26T00:00:00"))).doesNotThrowAnyException();
    }

    @Test
    void requireNotPast_noDueDate_isAllowed() {
        // A one-off needs no due date at all, and an absent value is not a past one.
        assertThatCode(() -> rule.requireNotPast(null)).doesNotThrowAnyException();
    }

    @Test
    void requireNotPast_theCallersOwnTodayWhileUtcHasMovedOn_isAllowed() {
        DueDateRule westOfUtc = new DueDateRule(
                Clock.fixed(Instant.parse("2026-11-26T02:00:00Z"), ZoneOffset.UTC));

        // 18:00 on the 25th at -08:00 is the 26th in UTC. Comparing in UTC would tell a caller
        // their own today is already past, which is what the offset on the field exists to prevent.
        assertThatCode(() -> westOfUtc.requireNotPast(
                OffsetDateTime.parse("2026-11-25T18:00:00-08:00"))).doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_theDateTheChoreAlreadyHad_isAllowed() {
        // The whole point of the looser edit rule: an overdue chore has to stay renameable and
        // reassignable, and a round written off as missed keeps its anchor parked in the past.
        assertThatCode(() -> rule.requireNotMovedIntoPast(
                utc("2026-11-20T09:00:00"), LocalDateTime.parse("2026-11-20T09:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_thatSameDayAtAnotherHour_isAllowed() {
        // Judged by date: a picker re-sending the stored day at its own midnight means the same
        // round, not a move onto a new one.
        assertThatCode(() -> rule.requireNotMovedIntoPast(
                utc("2026-11-20T00:00:00"), LocalDateTime.parse("2026-11-20T09:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_itsOwnEveningDueDateSentBackFromWestOfUtc_isAllowed() {
        // 23:59 on the 20th at -08:00 is stored as 07:59 on the 21st. Comparing that UTC date
        // against the caller's local one would call the chore's own date a move and refuse it.
        assertThatCode(() -> rule.requireNotMovedIntoPast(
                OffsetDateTime.parse("2026-11-20T23:59:00-08:00"),
                LocalDateTime.parse("2026-11-21T07:59")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_itsOwnAfterMidnightDueDateSentBackFromEastOfUtc_isAllowed() {
        // The same error the other way: 00:30 on the 20th at +01:00 is stored on the 19th.
        assertThatCode(() -> rule.requireNotMovedIntoPast(
                OffsetDateTime.parse("2026-11-20T00:30:00+01:00"),
                LocalDateTime.parse("2026-11-19T23:30")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_anotherDayInTheCallersOwnOffset_isRefused() {
        // And it still bites once the local day really differs, rather than passing everything
        // within a day of the stored value.
        assertThatThrownBy(() -> rule.requireNotMovedIntoPast(
                OffsetDateTime.parse("2026-11-19T23:59:00-08:00"),
                LocalDateTime.parse("2026-11-21T07:59")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requireNotMovedIntoPast_aDifferentPastDate_isRefused() {
        assertThatThrownBy(() -> rule.requireNotMovedIntoPast(
                utc("2026-11-19T09:00:00"), LocalDateTime.parse("2026-11-20T09:00:00")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void requireNotMovedIntoPast_aPastDateMovedForward_isAllowed() {
        assertThatCode(() -> rule.requireNotMovedIntoPast(
                utc("2026-12-01T09:00:00"), LocalDateTime.parse("2026-11-20T09:00:00")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotMovedIntoPast_aPastDateOnAChoreThatHadNone_isRefused() {
        // Nothing to have left unchanged, so this is a caller writing a late chore from scratch.
        assertThatThrownBy(() -> rule.requireNotMovedIntoPast(utc("2026-11-20T09:00:00"), null))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static OffsetDateTime utc(String local) {
        return LocalDateTime.parse(local).atOffset(ZoneOffset.UTC);
    }
}
