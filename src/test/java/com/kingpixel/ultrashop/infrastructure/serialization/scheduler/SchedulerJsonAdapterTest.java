package com.kingpixel.ultrashop.infrastructure.serialization.scheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.domain.scheduler.SchedulerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchedulerJsonAdapter}.
 *
 * <p>Covers four input shapes the adapter must accept:</p>
 * <ol>
 *   <li>New explicit format ({@code {type, expression|duration}})</li>
 *   <li>Legacy {@code RotationSchedule} format ({@code {cron, interval, amount}})</li>
 *   <li>Bare string primitives (cron-shaped or duration-shaped)</li>
 *   <li>Null / malformed input → falls back to default scheduler (no exceptions)</li>
 * </ol>
 *
 * <p>This is the data-loss-prevention guarantee: NO production config can break
 * shop loading because of a scheduler parsing failure.</p>
 */
class SchedulerJsonAdapterTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
            .registerTypeAdapter(Scheduler.class, new SchedulerJsonAdapter())
            .create();
    }

    // --- Forward serialization ---

    @Test
    void serialize_cronScheduler_writesTypeAndExpression() {
        String json = gson.toJson(new CronScheduler("0 18 * * 5"), Scheduler.class);

        assertTrue(json.contains("\"type\":\"CRON\""));
        assertTrue(json.contains("\"expression\":\"0 18 * * 5\""));
    }

    @Test
    void serialize_durationScheduler_writesTypeAndDuration() {
        String json = gson.toJson(new DurationScheduler("30m"), Scheduler.class);

        assertTrue(json.contains("\"type\":\"DURATION\""));
        assertTrue(json.contains("\"duration\":\"30m\""));
    }

    // --- Round trip ---

    @Test
    void roundTrip_cronScheduler() {
        CronScheduler original = new CronScheduler("*/15 * * * *");
        Scheduler decoded = gson.fromJson(gson.toJson(original, Scheduler.class), Scheduler.class);

        assertInstanceOf(CronScheduler.class, decoded);
        assertEquals(original, decoded);
    }

    @Test
    void roundTrip_durationScheduler() {
        DurationScheduler original = new DurationScheduler("4h");
        Scheduler decoded = gson.fromJson(gson.toJson(original, Scheduler.class), Scheduler.class);

        assertInstanceOf(DurationScheduler.class, decoded);
        assertEquals(original, decoded);
    }

    // --- New format deserialization ---

    @Test
    void deserialize_newFormatCron() {
        String json = "{\"type\":\"CRON\",\"expression\":\"0 0 * * *\"}";
        Scheduler s = gson.fromJson(json, Scheduler.class);

        assertInstanceOf(CronScheduler.class, s);
        assertEquals("0 0 * * *", ((CronScheduler) s).getExpression());
    }

    @Test
    void deserialize_newFormatDuration() {
        String json = "{\"type\":\"DURATION\",\"duration\":\"1h\"}";
        Scheduler s = gson.fromJson(json, Scheduler.class);

        assertInstanceOf(DurationScheduler.class, s);
        assertEquals("1h", ((DurationScheduler) s).getDuration());
    }

    // --- Legacy backwards compatibility ---

    @Test
    void deserialize_legacyRotationSchedule_cronOnly() {
        String json = "{\"cron\":\"0 18 * * 5\",\"interval\":null,\"amount\":3}";
        Scheduler s = gson.fromJson(json, Scheduler.class);

        assertInstanceOf(CronScheduler.class, s,
            "Legacy {cron, interval, amount} JSON must load without manual migration");
        assertEquals("0 18 * * 5", ((CronScheduler) s).getExpression());
    }

    @Test
    void deserialize_legacyRotationSchedule_intervalOnly() {
        String json = "{\"interval\":\"30m\",\"amount\":5}";
        Scheduler s = gson.fromJson(json, Scheduler.class);

        assertInstanceOf(DurationScheduler.class, s);
        assertEquals("30m", ((DurationScheduler) s).getDuration());
    }

    @Test
    void deserialize_legacyRotationSchedule_cronWinsOverInterval() {
        String json = "{\"cron\":\"0 * * * *\",\"interval\":\"30m\",\"amount\":3}";
        Scheduler s = gson.fromJson(json, Scheduler.class);

        assertInstanceOf(CronScheduler.class, s,
            "Cron must override interval — preserves legacy runtime behavior");
    }

    // --- Bare-string format ---

    @Test
    void deserialize_bareCronString() {
        Scheduler s = gson.fromJson("\"0 18 * * 5\"", Scheduler.class);

        assertInstanceOf(CronScheduler.class, s);
    }

    @Test
    void deserialize_bareDurationString() {
        Scheduler s = gson.fromJson("\"45m\"", Scheduler.class);

        assertInstanceOf(DurationScheduler.class, s);
    }

    // --- Robustness: malformed inputs do not throw ---

    @Test
    void deserialize_null_returnsDefault() {
        assertEquals(SchedulerType.CRON, gson.fromJson("null", Scheduler.class).getType());
    }

    @Test
    void deserialize_emptyObject_returnsDefault() {
        Scheduler s = gson.fromJson("{}", Scheduler.class);
        assertEquals(SchedulerType.CRON, s.getType(),
            "Unrecognized object must NOT throw — default scheduler is the safety net");
    }

    @Test
    void deserialize_garbageString_returnsDefault() {
        Scheduler s = gson.fromJson("\"not parseable as anything\"", Scheduler.class);
        assertEquals(SchedulerType.CRON, s.getType());
    }
}

