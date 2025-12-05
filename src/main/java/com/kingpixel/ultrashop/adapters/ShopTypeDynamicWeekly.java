package com.kingpixel.ultrashop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.models.Product;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.TypeShop;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamicWeekly extends ShopType implements JsonSerializer<ShopTypeDynamicWeekly>, JsonDeserializer<ShopTypeDynamicWeekly> {
  public static ShopTypeDynamicWeekly INSTANCE = new ShopTypeDynamicWeekly();
  private DurationValue cooldown;
  private int productsRotation;
  private List<DayOfWeek> days;

  public ShopTypeDynamicWeekly() {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    days = Arrays.stream(DayOfWeek.values()).toList();
    cooldown = DurationValue.parse("30m");
    productsRotation = 3;
  }

  public ShopTypeDynamicWeekly(DurationValue cooldown, int productsRotation, List<DayOfWeek> days) {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
    this.days = days;
  }

  @Override public void check() {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    if (cooldown == null) cooldown = DurationValue.parse("30m");
    productsRotation = Math.max(1, productsRotation);
    if (days == null) days = Arrays.stream(DayOfWeek.values()).toList();
  }

  @Override public List<Product> getProducts(Shop shop, ShopOptionsApi options) {
    return ShopTypeDynamic.getDynamicProducts(shop, options);
  }

  @Override public boolean isOpen() {
    return ShopTypeWeekly.canEnterDay(days);
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    String[] days = getDays().stream().map(DayOfWeek::toString).toArray(String[]::new);
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(UltraShop.dataShop.getActualCooldown(shop, shopOptionsApi)))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%amountProducts%", String.valueOf(this.getProductsRotation()))
      .replace("%days%", String.join(", ", days));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeDynamicWeekly src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    jsonObject.add("cooldown", DurationValue.INSTANCE.serialize(src.getCooldown(), DurationValue.class, context));
    jsonObject.addProperty("productsRotation", src.getProductsRotation());
    JsonArray daysArray = new JsonArray();
    for (DayOfWeek day : src.getDays()) {
      daysArray.add(day.toString());
    }
    jsonObject.add("days", daysArray);
    return jsonObject;
  }

  @Override
  public ShopTypeDynamicWeekly deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    JsonElement jsonCooldown = jsonObject.get("cooldown");
    DurationValue cooldown = (jsonCooldown != null)
      ? DurationValue.INSTANCE.deserialize(jsonCooldown, typeOfT, context)
      : DurationValue.parse("30m");

    JsonElement jsonProductsRotation = jsonObject.get("productsRotation");
    int productsRotation = (jsonProductsRotation != null) ? jsonProductsRotation.getAsInt() : 3; // Default value 3

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

    ShopTypeDynamicWeekly shopTypeDynamicWeekly = new ShopTypeDynamicWeekly(cooldown, productsRotation, days);
    shopTypeDynamicWeekly.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));

    return shopTypeDynamicWeekly;
  }
}