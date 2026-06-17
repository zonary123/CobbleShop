package com.kingpixel.ultrashop.domain.scheduler;

import com.kingpixel.ultrashop.domain.model.CronExpression;
import com.kingpixel.cobbleutils.Model.ScheduleValue;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Cron-based {@link Scheduler}. Fires according to a 5-field cron expression
 * (minute, hour, day-of-month, month, day-of-week).
 *
 * <p>The expression is parsed and validated at construction — invalid inputs
 * throw {@link IllegalArgumentException} immediately rather than failing at
 * the first {@link #nextFireTime(long)} call.</p>
 *
 * <p>The compiled {@link CronExpression} is held as a {@code transient} field
 * so it doesn't pollute serialization (Gson only writes {@link #expression}).</p>
 *
 * <p><b>Examples:</b></p>
 * <ul>
 *   <li>{@code new CronScheduler("0 * * * *")} — every hour, on the hour</li>
 *   <li>{@code new CronScheduler("0 18 * * 5")} — every Friday at 18:00</li>
 *   <li>{@code new CronScheduler("*}{@code /15 * * * *")} — every 15 minutes</li>
 * </ul>
 *
 * @see CronExpression
 */
@Getter
@EqualsAndHashCode(of = "expression")
public final class CronScheduler implements Scheduler {

  private final String expression;
  private final transient CronExpression compiled;

  /**
   * @param expression valid 5-field cron expression
   * @throws IllegalArgumentException if the expression is null, blank, or malformed
   */
  public CronScheduler(String expression) {
    Objects.requireNonNull(expression, "cron expression must not be null");
    this.expression = expression;
    this.compiled = CronExpression.parse(expression);
  }

  @Override
  public SchedulerType getType() {
    return SchedulerType.CRON;
  }

  @Override
  public long nextFireTime(long afterEpochMs) {
    try {
      return ScheduleValue.ofCron(expression, ZoneId.systemDefault().getId())
        .toNextEpochMillis(Instant.ofEpochMilli(afterEpochMs));
    } catch (Exception ignored) {
      return compiled.nextFireTime(afterEpochMs);
    }
  }

  @Override
  public String describe() {
    return "cron(" + expression + ")";
  }

  @Override
  public String toString() {
    return describe();
  }
}

