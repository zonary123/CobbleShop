package com.kingpixel.ultrashop.domain.scheduler;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;

/**
 * Centralized factory for {@link Scheduler} instances. Owns the legacy
 * compatibility layer that translates the deprecated {@link RotationSchedule}
 * (with both {@code cron} and {@code interval} fields coexisting) into the
 * new explicit {@link Scheduler} hierarchy.
 *
 * <p><b>Migration policy</b> implemented in {@link #fromLegacy(RotationSchedule)}:</p>
 * <ol>
 *   <li>If {@code cron} is non-blank → {@link CronScheduler}. Cron always wins —
 *       this matches the historical behavior where the cron field "overrode"
 *       the interval.</li>
 *   <li>Else if {@code interval} is non-blank → {@link DurationScheduler}.</li>
 *   <li>Else → {@link Scheduler#defaultScheduler()} (cron, hourly).</li>
 * </ol>
 *
 * <p>If parsing fails for the chosen path, the factory logs a warning and falls
 * back to the next strategy, then to the project default. This guarantees that
 * a malformed legacy config NEVER prevents a shop from loading — it just gets
 * a safe scheduler.</p>
 *
 * <p>Once Phase 4 cleanup removes {@code RotationSchedule}, this class loses
 * the {@code fromLegacy} method and becomes a thin instantiation helper.</p>
 */
public final class SchedulerFactory {

  private SchedulerFactory() {
    // utility class
  }

  /**
   * Translates a legacy {@link RotationSchedule} into the matching {@link Scheduler}.
   *
   * <p>Used by:</p>
   * <ul>
   *   <li>The Phase 3 {@code V2ToV3Migrator} when rewriting old shop JSON.</li>
   *   <li>Runtime adapters when reading shop JSON that still carries the legacy
   *       {@code rotationSchedule} object — the in-memory {@code RotationShop}
   *       gets a real {@link Scheduler} without any data loss.</li>
   * </ul>
   *
   * @param legacy non-null legacy schedule (callers must null-check upstream)
   * @return a Scheduler that preserves the original intent, with safe fallback
   */
  public static Scheduler fromLegacy(RotationSchedule legacy) {
    if (legacy == null) {
      return Scheduler.defaultScheduler();
    }

    Scheduler fromCron = tryCron(legacy.getCron());
    if (fromCron != null) {
      return fromCron;
    }

    Scheduler fromInterval = tryInterval(legacy.getInterval());
    if (fromInterval != null) {
      return fromInterval;
    }

    UltraShop.LOGGER.warn("Legacy RotationSchedule has neither valid cron nor interval; using default scheduler.");
    return Scheduler.defaultScheduler();
  }

  private static Scheduler tryCron(String cron) {
    if (cron == null || cron.isBlank()) {
      return null;
    }
    try {
      return new CronScheduler(cron);
    } catch (Exception e) {
      UltraShop.LOGGER.warn("Legacy cron expression '{}' is invalid: {} — falling back to interval.",
        cron, e.getMessage());
      return null;
    }
  }

  private static Scheduler tryInterval(String interval) {
    if (interval == null || interval.isBlank()) {
      return null;
    }
    try {
      return new DurationScheduler(interval);
    } catch (Exception e) {
      UltraShop.LOGGER.warn("Legacy interval '{}' is invalid: {} — falling back to default scheduler.",
        interval, e.getMessage());
      return null;
    }
  }

  /**
   * Builds a {@link CronScheduler} from a user-supplied cron expression.
   * Used by the admin editor when the player types a new schedule via chat.
   *
   * @param cron non-blank cron expression (5-field standard syntax)
   * @return a validated {@link CronScheduler}
   * @throws IllegalArgumentException if {@code cron} is null/blank or fails to parse
   */
  public static Scheduler fromCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new IllegalArgumentException("Cron expression must be non-blank");
    }
    return new CronScheduler(cron);
  }

  /**
   * Builds a {@link DurationScheduler} from a user-supplied interval expression
   * (e.g. {@code "30m"}, {@code "1h"}, {@code "7d"}).
   *
   * @param interval non-blank duration expression
   * @return a validated {@link DurationScheduler}
   * @throws IllegalArgumentException if {@code interval} is null/blank or fails to parse
   */
  public static Scheduler fromInterval(String interval) {
    if (interval == null || interval.isBlank()) {
      throw new IllegalArgumentException("Interval expression must be non-blank");
    }
    return new DurationScheduler(interval);
  }
}

