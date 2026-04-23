package com.kingpixel.ultrashop.domain.scheduler;

import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchedulerFactory#fromLegacy(RotationSchedule)} — the migration
 * bridge from the deprecated {@link RotationSchedule} to the {@link Scheduler} hierarchy.
 *
 * <p>These tests pin down the data-loss-prevention contract: ANY legacy schedule,
 * even malformed, must produce SOME {@link Scheduler} — never throw, never return null.</p>
 */
class SchedulerFactoryTest {

    @Test
    void nullLegacy_returnsDefaultScheduler() {
        Scheduler s = SchedulerFactory.fromLegacy(null);
        assertEquals(SchedulerType.CRON, s.getType());
    }

    @Test
    void cronOnly_returnsCronScheduler() {
        RotationSchedule legacy = new RotationSchedule();
        legacy.setCron("0 18 * * 5");

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertInstanceOf(CronScheduler.class, s);
        assertEquals("0 18 * * 5", ((CronScheduler) s).getExpression());
    }

    @Test
    void intervalOnly_returnsDurationScheduler() {
        RotationSchedule legacy = new RotationSchedule("30m", 3);

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertInstanceOf(DurationScheduler.class, s);
        assertEquals("30m", ((DurationScheduler) s).getDuration());
    }

    @Test
    void cronWinsOverInterval_matchingHistoricalBehavior() {
        RotationSchedule legacy = new RotationSchedule("30m", 3);
        legacy.setCron("0 * * * *");

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertInstanceOf(CronScheduler.class, s,
            "Legacy code had cron override interval — migration must preserve this");
    }

    @Test
    void invalidCron_fallsBackToInterval() {
        RotationSchedule legacy = new RotationSchedule("1h", 3);
        legacy.setCron("garbage cron");

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertInstanceOf(DurationScheduler.class, s,
            "Bad cron must not block the shop — interval is the fallback");
    }

    @Test
    void invalidCronAndInvalidInterval_fallsBackToDefault() {
        RotationSchedule legacy = new RotationSchedule("not a duration", 3);
        legacy.setCron("garbage");

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertEquals(SchedulerType.CRON, s.getType(),
            "Both invalid → default scheduler (CRON hourly), never null/throw");
    }

    @Test
    void emptyStrings_treatedAsAbsent() {
        RotationSchedule legacy = new RotationSchedule();
        legacy.setCron("");
        legacy.setInterval("   ");

        Scheduler s = SchedulerFactory.fromLegacy(legacy);

        assertEquals(SchedulerType.CRON, s.getType());
    }
}

