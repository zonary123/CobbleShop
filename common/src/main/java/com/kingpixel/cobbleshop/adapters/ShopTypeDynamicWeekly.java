package com.kingpixel.cobbleshop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamicWeekly extends ShopType implements JsonSerializer<ShopTypeDynamicWeekly>, JsonDeserializer<ShopTypeDynamicWeekly> {
  public static ShopTypeDynamicWeekly INSTANCE = new ShopTypeDynamicWeekly();
  private int cooldown;
  private int productsRotation;
  private List<DayOfWeek> days;

  public ShopTypeDynamicWeekly() {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    days = Arrays.stream(DayOfWeek.values()).toList();
    cooldown = 30;
    productsRotation = 3;
  }

  public ShopTypeDynamicWeekly(int cooldown, int productsRotation, List<DayOfWeek> days) {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
    this.days = days;
  }

  @Override public void check() {
    setTypeShop(TypeShop.DYNAMIC_WEEKLY);
    cooldown = Math.max(1, cooldown);
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
      .replace("%cooldown%", PlayerUtils.getCooldown(new Date(CobbleShop.dataShop.getActualCooldown(shop, shopOptionsApi))))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%amountProducts%", String.valueOf(this.getProductsRotation()))
      .replace("%days%", String.join(", ", days));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeDynamicWeekly src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    jsonObject.addProperty("cooldown", src.getCooldown());
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
    if (jsonCooldown == null) {
      jsonCooldown = jsonObject.get("minutes");
    }
    int cooldown = jsonCooldown.getAsInt();
    JsonElement jsonProductsRotation = jsonObject.get("productsRotation");
    if (jsonProductsRotation == null) {
      jsonProductsRotation = jsonObject.get("amountProducts");
    }
    int productsRotation = jsonProductsRotation.getAsInt();
    JsonArray daysArray = jsonObject.getAsJsonArray("days");
    if (daysArray == null) {
      daysArray = jsonObject.getAsJsonArray("dayOfWeek");
    }
    List<DayOfWeek> days = new ArrayList<>();
    for (JsonElement dayElement : daysArray) {
      days.add(DayOfWeek.valueOf(dayElement.getAsString()));
    }
    ShopTypeDynamicWeekly shopTypeDynamicWeekly = new ShopTypeDynamicWeekly(cooldown, productsRotation, days);
    shopTypeDynamicWeekly.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));
    return shopTypeDynamicWeekly;
  }
}