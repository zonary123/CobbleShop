package com.kingpixel.ultrashop.domain.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.BitSet;

/**
 * Minimal 5-field cron expression parser: {@code "minute hour day-of-month month day-of-week"}.
 *
 * <p>Supports {@code *}, {@code n}, {@code a-b}, {@code a,b,c} and {@code *}{@code /n} (step).
 * Day-of-week uses 0-6 where 0=Sunday (also accepts 7=Sunday).</p>
 *
 * <p>Used by {@link RotationSchedule#getCron()} to compute the next rotation
 * timestamp. If the expression is invalid, throws {@link IllegalArgumentException}
 * so the caller can fall back to {@code interval}.</p>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code "0 18 * * 5"} - every Friday at 18:00</li>
 *   <li>{@code "0 * * * *"} - top of every hour</li>
 *   <li>{@code "*}{@code /15 * * * *"} - every 15 minutes</li>
 *   <li>{@code "0 0,12 * * *"} - at 00:00 and 12:00 every day</li>
 * </ul>
 */
public final class CronExpression {

  private final BitSet minutes;       // 0-59
  private final BitSet hours;         // 0-23
  private final BitSet daysOfMonth;   // 1-31
  private final BitSet months;        // 1-12
  private final BitSet daysOfWeek;    // 0-6 (Sun=0)
  private final String original;

  private CronExpression(String original, BitSet min, BitSet hr, BitSet dom, BitSet mo, BitSet dow) {
    this.original = original;
    this.minutes = min;
    this.hours = hr;
    this.daysOfMonth = dom;
    this.months = mo;
    this.daysOfWeek = dow;
  }

  public static CronExpression parse(String expr) {
    if (expr == null || expr.isBlank()) {
      throw new IllegalArgumentException("Cron expression is empty");
    }
    String[] parts = expr.trim().split("\\s+");
    if (parts.length != 5) {
      throw new IllegalArgumentException("Cron must have 5 fields, got " + parts.length + ": " + expr);
    }
    return new CronExpression(
      expr,
      parseField(parts[0], 0, 59),
      parseField(parts[1], 0, 23),
      parseField(parts[2], 1, 31),
      parseField(parts[3], 1, 12),
      parseDayOfWeek(parts[4])
    );
  }

  private static BitSet parseDayOfWeek(String field) {
    // Accept 7 as Sunday and normalize to 0
    BitSet bs = parseField(field.replace("7", "0"), 0, 6);
    return bs;
  }

  private static BitSet parseField(String field, int min, int max) {
    BitSet bs = new BitSet(max + 1);
    for (String token : field.split(",")) {
      int step = 1;
      String range = token;
      int slash = token.indexOf('/');
      if (slash >= 0) {
        step = Integer.parseInt(token.substring(slash + 1));
        if (step <= 0) throw new IllegalArgumentException("Invalid step: " + token);
        range = token.substring(0, slash);
      }
      int from, to;
      if ("*".equals(range)) {
        from = min;
        to = max;
      } else if (range.contains("-")) {
        String[] rangeParts = range.split("-");
        from = Integer.parseInt(rangeParts[0]);
        to = Integer.parseInt(rangeParts[1]);
      } else {
        from = to = Integer.parseInt(range);
      }
      if (from < min || to > max || from > to) {
        throw new IllegalArgumentException("Field out of range [" + min + "," + max + "]: " + token);
      }
      for (int i = from; i <= to; i += step) bs.set(i);
    }
    return bs;
  }

  /**
   * Returns the next epoch-millis (system time-zone) at which this cron fires
   * strictly after {@code afterEpochMs}. Searches up to 366 days ahead.
   *
   * @throws IllegalStateException if no valid time is found within 366 days
   */
  public long nextFireTime(long afterEpochMs) {
    ZoneId zone = ZoneId.systemDefault();
    LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterEpochMs), zone);
    // Start from next full minute
    LocalDateTime cursor = now.withSecond(0).withNano(0).plusMinutes(1);
    LocalDateTime limit = cursor.plusDays(366);

    while (cursor.isBefore(limit)) {
      if (matches(cursor)) {
        return cursor.atZone(zone).toInstant().toEpochMilli();
      }
      cursor = cursor.plusMinutes(1);
    }
    throw new IllegalStateException("No matching time within 366 days for cron: " + original);
  }

  private boolean matches(LocalDateTime t) {
    int dow = t.getDayOfWeek().getValue() % 7; // ISO Mon=1..Sun=7 → Sun=0,Mon=1..Sat=6
    return minutes.get(t.getMinute())
      && hours.get(t.getHour())
      && daysOfMonth.get(t.getDayOfMonth())
      && months.get(t.getMonthValue())
      && daysOfWeek.get(dow);
  }

  @Override
  public String toString() {
    return original;
  }
}


