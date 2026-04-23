package com.kingpixel.ultrashop.domain.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CronScheduler}.
 */
class CronSchedulerTest {

    @Test
    void getType_returnsCron() {
        assertEquals(SchedulerType.CRON, new CronScheduler("0 * * * *").getType());
    }

    @Test
    void invalidExpression_throwsAtConstruction() {
        assertThrows(IllegalArgumentException.class,
            () -> new CronScheduler("not a cron"));
    }

    @Test
    void nullExpression_throwsNpe() {
        assertThrows(NullPointerException.class,
            () -> new CronScheduler(null));
    }

    @Test
    void nextFireTime_isStrictlyAfterReference() {
        CronScheduler s = new CronScheduler("0 * * * *"); // top of every hour
        long now = System.currentTimeMillis();
        long next = s.nextFireTime(now);

        assertTrue(next > now, "next fire must be strictly after reference");
    }

    @Test
    void nextFireTime_topOfHour_isWallclockAligned() {
        // Reference: 2026-01-15 10:30:00 (system zone).
        long ref = LocalDateTime.of(2026, 1, 15, 10, 30, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long expected = LocalDateTime.of(2026, 1, 15, 11, 0, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        long next = new CronScheduler("0 * * * *").nextFireTime(ref);

        assertEquals(expected, next);
    }

    @Test
    void describe_includesExpression() {
        assertEquals("cron(0 18 * * 5)", new CronScheduler("0 18 * * 5").describe());
    }

    @Test
    void equality_isStructural() {
        assertEquals(new CronScheduler("0 * * * *"), new CronScheduler("0 * * * *"));
        assertNotEquals(new CronScheduler("0 * * * *"), new CronScheduler("0 12 * * *"));
    }
}

