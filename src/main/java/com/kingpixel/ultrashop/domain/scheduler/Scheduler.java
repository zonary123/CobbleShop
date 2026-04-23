package com.kingpixel.ultrashop.domain.scheduler;

/**
 * Strategy interface that decides WHEN something should fire next.
 *
 * <p>This is the polymorphic abstraction that replaces the legacy
 * {@code RotationSchedule} field-flags pattern (where {@code cron} silently
 * "won" over {@code interval}). Each implementation is selected by an explicit
 * {@link SchedulerType} discriminator carried in the JSON.</p>
 *
 * <p>Sealed to enable exhaustive pattern matching in adapters and future
 * service code (Java 21+).</p>
 *
 * <p><b>Thread-safety:</b> implementations MUST be immutable and safe for use
 * from multiple threads — they are typically stored on long-lived shop objects
 * that are read concurrently by GUI / scheduler ticks.</p>
 */
public sealed interface Scheduler
  permits CronScheduler, DurationScheduler {

  /**
   * Returns the discriminator identifying this scheduler's strategy.
   * Used by the JSON adapter to dispatch (de)serialization.
   */
  SchedulerType getType();

  /**
   * Computes the next fire timestamp (epoch millis) STRICTLY after the given
   * reference timestamp.
   *
   * @param afterEpochMs reference timestamp (typically {@code System.currentTimeMillis()})
   * @return epoch-millis of the next fire moment
   * @throws IllegalStateException if no valid next time can be computed
   *         (e.g. cron with no matches in the next 366 days)
   */
  long nextFireTime(long afterEpochMs);

  /**
   * Human-readable representation for logs / GUI tooltips.
   * MUST NOT be parsed — use the adapter for round-tripping.
   */
  String describe();

  /**
   * Project-wide default: cron firing at the top of every hour.
   * Used by callers that need "some sane scheduler" without having a specific one.
   */
  static Scheduler defaultScheduler() {
    return new CronScheduler("0 * * * *");
  }
}

