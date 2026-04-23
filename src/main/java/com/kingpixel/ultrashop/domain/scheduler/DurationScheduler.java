package com.kingpixel.ultrashop.domain.scheduler;

import com.kingpixel.cobbleutils.Model.DurationValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * Fixed-interval {@link Scheduler}. Fires every N milliseconds after the reference
 * timestamp, where N is parsed from a human-readable duration string.
 *
 * <p>Delegates parsing to CobbleUtils' {@link DurationValue} so the project's
 * existing duration syntax is honored 1:1 (e.g. {@code "30m"}, {@code "4h"},
 * {@code "1d"}, {@code "1d12h30m"}).</p>
 *
 * <p>The duration is parsed and cached at construction. Invalid inputs throw
 * {@link IllegalArgumentException} immediately.</p>
 *
 * <p><b>Caveat:</b> {@link #nextFireTime(long)} returns {@code afterEpochMs +
 * durationMs} — it does NOT track wallclock alignment. Best for "every X minutes
 * regardless of when the server started" rather than "at the top of every hour".
 * For wallclock-aligned scheduling use {@link CronScheduler}.</p>
 */
@Getter
@EqualsAndHashCode(of = "duration")
public final class DurationScheduler implements Scheduler {

  private final String duration;
  private final transient long durationMs;

  /**
   * @param duration human-readable duration ({@code "30m"}, {@code "4h"}, ...)
   * @throws IllegalArgumentException if the duration is null, blank, malformed,
   *         or non-positive
   */
  public DurationScheduler(String duration) {
    Objects.requireNonNull(duration, "duration must not be null");
    this.duration = duration;
    long ms = DurationValue.parse(duration).toMillis();
    if (ms <= 0) {
      throw new IllegalArgumentException(
        "Duration must resolve to a positive number of milliseconds: " + duration);
    }
    this.durationMs = ms;
  }

  @Override
  public SchedulerType getType() {
    return SchedulerType.DURATION;
  }

  @Override
  public long nextFireTime(long afterEpochMs) {
    return afterEpochMs + durationMs;
  }

  @Override
  public String describe() {
    return "every(" + duration + ")";
  }

  @Override
  public String toString() {
    return describe();
  }
}

