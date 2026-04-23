package com.kingpixel.ultrashop.infrastructure.serialization.scheduler;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.domain.scheduler.SchedulerFactory;
import com.kingpixel.ultrashop.domain.scheduler.SchedulerType;

import java.lang.reflect.Type;

/**
 * Gson (de)serializer for the polymorphic {@link Scheduler} hierarchy.
 *
 * <p><b>Output format (forward):</b></p>
 * <pre>
 *   { "type": "CRON",     "expression": "0 18 * * 5" }
 *   { "type": "DURATION", "duration":   "30m" }
 * </pre>
 *
 * <p><b>Input formats accepted (backwards-compat):</b></p>
 * <ol>
 *   <li><b>New format</b> — same as output, with explicit {@code "type"} discriminator.</li>
 *   <li><b>Legacy {@link RotationSchedule}</b> — JSON like
 *       {@code { "cron": "...", "interval": "...", "amount": N }}. Translated via
 *       {@link SchedulerFactory#fromLegacy(RotationSchedule)} so configs in
 *       production keep loading without manual migration. The {@code amount}
 *       field is intentionally ignored at this layer (it belongs to the shop,
 *       not to the scheduler).</li>
 *   <li><b>Bare cron string</b> — a JSON primitive like {@code "0 18 * * 5"}
 *       is interpreted as {@link CronScheduler}.</li>
 *   <li><b>Bare duration string</b> — recognized when the primitive ends in
 *       a unit char ({@code s/m/h/d}) — interpreted as {@link DurationScheduler}.</li>
 * </ol>
 *
 * <p>If none of the formats match, the adapter logs a warning and returns
 * {@link Scheduler#defaultScheduler()} rather than throwing — guaranteeing a
 * malformed config does NOT prevent the parent shop from loading.</p>
 */
public final class SchedulerJsonAdapter implements JsonSerializer<Scheduler>, JsonDeserializer<Scheduler> {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_EXPRESSION = "expression";
  private static final String FIELD_DURATION = "duration";
  private static final String LEGACY_FIELD_CRON = "cron";
  private static final String LEGACY_FIELD_INTERVAL = "interval";
  private static final String LEGACY_FIELD_AMOUNT = "amount";

  // --- Serialize ------------------------------------------------------------

  @Override
  public JsonElement serialize(Scheduler src, Type typeOfSrc, JsonSerializationContext ctx) {
    JsonObject obj = new JsonObject();
    obj.addProperty(FIELD_TYPE, src.getType().name());
    if (src instanceof CronScheduler cron) {
      obj.addProperty(FIELD_EXPRESSION, cron.getExpression());
    } else if (src instanceof DurationScheduler dur) {
      obj.addProperty(FIELD_DURATION, dur.getDuration());
    } else {
      // Sealed interface — exhaustive in practice, but keep a fail-safe.
      throw new JsonParseException("Unknown Scheduler implementation: " + src.getClass());
    }
    return obj;
  }

  // --- Deserialize ----------------------------------------------------------

  @Override
  public Scheduler deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
    if (json == null || json.isJsonNull()) {
      return Scheduler.defaultScheduler();
    }
    if (json.isJsonPrimitive()) {
      return fromPrimitive(json.getAsJsonPrimitive());
    }
    if (json.isJsonObject()) {
      return fromObject(json.getAsJsonObject());
    }
    UltraShop.LOGGER.warn("Unsupported JSON shape for Scheduler: {} — using default.", json);
    return Scheduler.defaultScheduler();
  }

  private Scheduler fromObject(JsonObject obj) {
    // 1) New format: explicit discriminator.
    if (obj.has(FIELD_TYPE)) {
      try {
        SchedulerType type = SchedulerType.valueOf(obj.get(FIELD_TYPE).getAsString());
        return switch (type) {
          case CRON -> new CronScheduler(obj.get(FIELD_EXPRESSION).getAsString());
          case DURATION -> new DurationScheduler(obj.get(FIELD_DURATION).getAsString());
        };
      } catch (Exception e) {
        UltraShop.LOGGER.warn("Failed to parse Scheduler with explicit type {}: {} — trying legacy.",
          obj.get(FIELD_TYPE), e.getMessage());
      }
    }

    // 2) Legacy format: RotationSchedule shape ({cron, interval, amount}).
    if (obj.has(LEGACY_FIELD_CRON) || obj.has(LEGACY_FIELD_INTERVAL) || obj.has(LEGACY_FIELD_AMOUNT)) {
      RotationSchedule legacy = new RotationSchedule();
      if (obj.has(LEGACY_FIELD_CRON) && !obj.get(LEGACY_FIELD_CRON).isJsonNull()) {
        legacy.setCron(obj.get(LEGACY_FIELD_CRON).getAsString());
      }
      if (obj.has(LEGACY_FIELD_INTERVAL) && !obj.get(LEGACY_FIELD_INTERVAL).isJsonNull()) {
        legacy.setInterval(obj.get(LEGACY_FIELD_INTERVAL).getAsString());
      }
      return SchedulerFactory.fromLegacy(legacy);
    }

    UltraShop.LOGGER.warn("JSON object has no recognizable Scheduler fields: {} — using default.", obj);
    return Scheduler.defaultScheduler();
  }

  private Scheduler fromPrimitive(JsonPrimitive prim) {
    if (!prim.isString()) {
      UltraShop.LOGGER.warn("Non-string primitive for Scheduler: {} — using default.", prim);
      return Scheduler.defaultScheduler();
    }
    String value = prim.getAsString().trim();
    if (value.isEmpty()) {
      return Scheduler.defaultScheduler();
    }
    // Heuristic: 5 space-separated tokens → cron; otherwise → duration.
    if (value.split("\\s+").length == 5) {
      try {
        return new CronScheduler(value);
      } catch (Exception ignored) {
        // fall through to duration attempt
      }
    }
    try {
      return new DurationScheduler(value);
    } catch (Exception e) {
      UltraShop.LOGGER.warn("Bare scheduler string '{}' is neither valid cron nor duration: {} — using default.",
        value, e.getMessage());
      return Scheduler.defaultScheduler();
    }
  }
}

