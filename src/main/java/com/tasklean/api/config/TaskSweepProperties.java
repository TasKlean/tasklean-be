package com.tasklean.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * How the missed-round sweep behaves. Separate from the schedule arithmetic so the policy — how
 * long a chore may be recorded late, and how much catching up one run will do — is configuration
 * rather than code.
 */
@Configuration
@ConfigurationProperties(prefix = "tasks.sweep")
@Getter
@Setter
public class TaskSweepProperties {

    /**
     * Extra whole days a round stays recordable after its due date. Zero means a chore is
     * recordable for the whole of its due date and written off once the next day starts, matching
     * the date comparison the recording guard already uses.
     *
     * <p>Raising it is the only way to let somebody record a round late: a miss takes the round's
     * slot in {@code uq_tc_task_due}, so once written, that round cannot be recorded at all.
     */
    private int graceDays = 0;

    /** How many tasks one run will look at. Each is swept in its own transaction. */
    private int batch = 100;

    /**
     * The most rounds one run will write off for a single task. A chore neglected for longer catches
     * up over successive runs rather than writing an unbounded number of rows in one transaction.
     */
    private int maxRoundsPerTask = 30;
}
