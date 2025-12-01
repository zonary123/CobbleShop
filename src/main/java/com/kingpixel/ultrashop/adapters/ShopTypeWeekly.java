package com.kingpixel.ultrashop.adapters;

import com.google.gson.*;
import com.kingpixel.ultrashop.api.ShopApi;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.TypeShop;
import com.kingpixel.cobbleutils.CobbleUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeWeekly extends ShopType implements JsonSerializer<ShopTypeWeekly>, JsonDeserializer<ShopTypeWeekly> {
  public static ShopTypeWeekly INSTANCE = new ShopTypeWeekly();
  private List<DayOfWeek> days;

  public ShopTypeWeekly() {
    setTypeShop(TypeShop.WEEKLY);
    days = Arrays.stream(DayOfWeek.values()).toList();
  }

  public ShopTypeWeekly(List<DayOfWeek> days) {
    setTypeShop(TypeShop.WEEKLY);
    this.days = days;
  }

  @Override public void check() {
    setTypeShop(TypeShop.WEEKLY);
    if (days == null) days = Arrays.stream(DayOfWeek.values()).toList();
  }

  @Override public boolean isOpen() {
    return canEnterDay(days);
  }

  public static boolean canEnterDay(List<DayOfWeek> days) {
    if (days == null || days.isEmpty()) return true; // If no days are specified, assume open every day
    if (ShopApi.getMainConfig().isDebug()) {
      CobbleUtils.LOGGER.info("Checking if shop is open today. Days: " + days);
    }
    return days.contains(DayOfWeek.from(LocalDate.now()));
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    String[] days = getDays().stream().map(DayOfWeek::toString).toArray(String[]::new);
    return text
      .replace("%days%", String.join(", ", days));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeWeekly src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    JsonArray daysArray = new JsonArray();
    for (DayOfWeek day : src.getDays()) {
      daysArray.add(day.toString());
    }
    jsonObject.add("days", daysArray);
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    return jsonObject;
  }

  @Override
  public ShopTypeWeekly deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    JsonArray daysArray = jsonObject.getAsJsonArray("days");
    if (daysArray == null) {
      daysArray = jsonObject.getAsJsonArray("dayOfWeek");
    }
    List<DayOfWeek> days = new ArrayList<>();
    if (daysArray != null) {
      for (JsonElement dayElement : daysArray) {
        days.add(DayOfWeek.valueOf(dayElement.getAsString()));
      }
    } else {
      days = Arrays.stream(DayOfWeek.values()).toList(); // Default to all days
    }
    ShopTypeWeekly shopTypeWeekly = new ShopTypeWeekly(days);
    shopTypeWeekly.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));
    return shopTypeWeekly;
  }
}