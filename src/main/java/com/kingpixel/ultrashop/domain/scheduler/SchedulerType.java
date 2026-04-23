package com.kingpixel.ultrashop.domain.scheduler;

/**
 * Discriminator for the scheduling strategy used by a {@code RotationShop} (or any
 * other future consumer of {@link Scheduler}).
 *
 * <p>The enum value is serialized as the {@code "type"} discriminator field in JSON,
 * driving polymorphic deserialization to the matching {@link Scheduler} implementation.</p>
 *
 * <ul>
 *   <li>{@link #CRON} — Default. Wallclock-driven scheduling using a 5-field cron
 *       expression. Survives server restarts (next fire is computed from current
 *       time, not from a persisted offset). Best for "every Friday at 18:00",
 *       "daily reset", and other calendar-aligned events.</li>
 *   <li>{@link #DURATION} — Relative interval (e.g. {@code "30m"}, {@code "4h"}).
 *       Each fire = previous fire + duration. Simpler to configure but drifts with
 *       restarts. Best for "every hour, no matter when".</li>
 * </ul>
 */
public enum SchedulerType {
  /** Cron-based scheduling — project default. */
  CRON,
  /** Fixed-duration interval scheduling. */
  DURATION
}

