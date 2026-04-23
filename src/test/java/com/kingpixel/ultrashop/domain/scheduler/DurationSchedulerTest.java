package com.kingpixel.ultrashop.domain.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DurationScheduler}.
 */
class DurationSchedulerTest {

    @Test
    void getType_returnsDuration() {
        assertEquals(SchedulerType.DURATION, new DurationScheduler("30m").getType());
    }

    @Test
    void nullDuration_throwsNpe() {
        assertThrows(NullPointerException.class, () -> new DurationScheduler(null));
    }

    @Test
    void invalidDuration_throwsIllegalArgument() {
        assertThrows(RuntimeException.class, () -> new DurationScheduler("not a duration"));
    }

    @Test
    void nextFireTime_addsExactDurationMillis() {
        long ref = 1_700_000_000_000L; // arbitrary fixed point
        long next = new DurationScheduler("30m").nextFireTime(ref);

        assertEquals(ref + 30L * 60L * 1000L, next, "30m must add exactly 1_800_000ms");
    }

    @Test
    void nextFireTime_isMonotonicIncreasing() {
        DurationScheduler s = new DurationScheduler("1h");
        long t1 = s.nextFireTime(0L);
        long t2 = s.nextFireTime(t1);

        assertTrue(t2 > t1, "consecutive fires must increase");
        assertEquals(3_600_000L, t2 - t1);
    }

    @Test
    void describe_includesDuration() {
        assertEquals("every(2h)", new DurationScheduler("2h").describe());
    }

    @Test
    void equality_isStructural() {
        assertEquals(new DurationScheduler("1h"), new DurationScheduler("1h"));
        assertNotEquals(new DurationScheduler("1h"), new DurationScheduler("2h"));
    }
}


