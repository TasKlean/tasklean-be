package com.tasklean.api.domain.task;

import com.tasklean.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceScheduleTest {

    private final RecurrenceSchedule schedule = new RecurrenceSchedule();

    private static Map<String, Object> onDay(int dayOfMonth) {
        return Map.of("dayOfMonth", dayOfMonth);
    }

    @Test
    void next_daily_movesOnADay() {
        assertThat(schedule.next(RecurrenceType.DAILY, null, LocalDateTime.parse("2026-11-19T08:00")))
                .isEqualTo(LocalDateTime.parse("2026-11-20T08:00"));
    }

    @Test
    void next_weekly_movesOnAWeek() {
        assertThat(schedule.next(RecurrenceType.WEEKLY, null, LocalDateTime.parse("2026-11-19T08:00")))
                .isEqualTo(LocalDateTime.parse("2026-11-26T08:00"));
    }

    @Test
    void next_weekly_staysOnTheSameWeekday() {
        LocalDateTime from = LocalDateTime.parse("2026-11-19T08:00");

        LocalDateTime due = schedule.next(RecurrenceType.WEEKLY, null, from);

        // The weekly rule exists so nobody has to store a day-of-week that could drift from it.
        assertThat(due.getDayOfWeek()).isEqualTo(from.getDayOfWeek());
    }

    @Test
    void next_preservesTimeOfDay() {
        assertThat(schedule.next(RecurrenceType.DAILY, null, LocalDateTime.parse("2026-11-19T06:45:30")))
                .isEqualTo(LocalDateTime.parse("2026-11-20T06:45:30"));
    }

    @Test
    void next_monthly_keepsTheDayOfMonth() {
        assertThat(schedule.next(RecurrenceType.MONTHLY, onDay(15), LocalDateTime.parse("2026-03-15T09:00")))
                .isEqualTo(LocalDateTime.parse("2026-04-15T09:00"));
    }

    @Test
    void next_monthly_shortMonth_clampsToItsLastDay() {
        assertThat(schedule.next(RecurrenceType.MONTHLY, onDay(31), LocalDateTime.parse("2026-01-31T09:00")))
                .isEqualTo(LocalDateTime.parse("2026-02-28T09:00"));
    }

    @Test
    void next_monthly_afterAClamp_returnsToTheAnchorDay() {
        // The reason the anchor is stored at all: stepping on from the clamped date alone would
        // hold the task at the 28th for every month after this one.
        assertThat(schedule.next(RecurrenceType.MONTHLY, onDay(31), LocalDateTime.parse("2026-02-28T09:00")))
                .isEqualTo(LocalDateTime.parse("2026-03-31T09:00"));
    }

    @Test
    void next_monthly_leapFebruary_usesThe29th() {
        assertThat(schedule.next(RecurrenceType.MONTHLY, onDay(29), LocalDateTime.parse("2028-01-29T09:00")))
                .isEqualTo(LocalDateTime.parse("2028-02-29T09:00"));
    }

    @Test
    void next_monthly_noAnchorRecorded_keepsTheDayItIsDueOn() {
        assertThat(schedule.next(RecurrenceType.MONTHLY, null, LocalDateTime.parse("2026-03-09T09:00")))
                .isEqualTo(LocalDateTime.parse("2026-04-09T09:00"));
    }

    @Test
    void next_monthly_anchorBeyondAnyMonth_isClampedIntoIt() {
        assertThat(schedule.next(RecurrenceType.MONTHLY, onDay(99), LocalDateTime.parse("2026-03-15T09:00")))
                .isEqualTo(LocalDateTime.parse("2026-04-30T09:00"));
    }

    @Test
    void next_custom_isRefused() {
        assertThatThrownBy(() ->
                schedule.next(RecurrenceType.CUSTOM, null, LocalDateTime.parse("2026-11-19T08:00")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rollForward_oneStepClearsTheCutoff_takesIt() {
        LocalDateTime due = schedule.rollForward(RecurrenceType.DAILY, null,
                LocalDateTime.parse("2026-11-19T08:00"), LocalDateTime.parse("2026-11-19T12:00"));

        assertThat(due).isEqualTo(LocalDateTime.parse("2026-11-20T08:00"));
    }

    @Test
    void rollForward_longNeglected_landsJustPastTheCutoffRatherThanOnIt() {
        // Ten days of a daily chore nobody did: the answer is the next real due date, keeping its
        // time of day, not the moment it was finally caught up on.
        LocalDateTime due = schedule.rollForward(RecurrenceType.DAILY, null,
                LocalDateTime.parse("2026-11-09T07:00"), LocalDateTime.parse("2026-11-19T12:00"));

        assertThat(due).isEqualTo(LocalDateTime.parse("2026-11-20T07:00"));
    }

    @Test
    void rollForward_countsFromTheDueDateNotTheCutoff() {
        LocalDateTime from = LocalDateTime.parse("2026-11-02T08:00");

        LocalDateTime due = schedule.rollForward(RecurrenceType.WEEKLY, null,
                from, LocalDateTime.parse("2026-11-19T12:00"));

        // Three weeks on, and still the same weekday - a schedule counted from the cutoff would
        // have moved the chore to whichever day it was caught up on.
        assertThat(due).isEqualTo(LocalDateTime.parse("2026-11-23T08:00"));
        assertThat(due.getDayOfWeek()).isEqualTo(from.getDayOfWeek());
    }

    @Test
    void rollForward_monthly_skipsPastEveryMissedMonth() {
        LocalDateTime due = schedule.rollForward(RecurrenceType.MONTHLY, onDay(31),
                LocalDateTime.parse("2025-12-31T09:00"), LocalDateTime.parse("2026-03-15T00:00"));

        assertThat(due).isEqualTo(LocalDateTime.parse("2026-03-31T09:00"));
    }

    @Test
    void anchorFor_monthly_recordsTheFirstDueDay() {
        assertThat(schedule.anchorFor(RecurrenceType.MONTHLY, LocalDateTime.parse("2026-01-31T09:00")))
                .containsExactly(Map.entry("dayOfMonth", 31));
    }

    @Test
    void anchorFor_dailyAndWeekly_recordNothing() {
        LocalDateTime firstDue = LocalDateTime.parse("2026-01-31T09:00");

        assertThat(schedule.anchorFor(RecurrenceType.DAILY, firstDue)).isNull();
        assertThat(schedule.anchorFor(RecurrenceType.WEEKLY, firstDue)).isNull();
    }
}
