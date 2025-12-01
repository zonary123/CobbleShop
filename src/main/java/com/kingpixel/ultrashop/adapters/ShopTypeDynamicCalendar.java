package com.kingpixel.ultrashop.adapters;

import com.google.gson.*;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.models.DateRange;
import com.kingpixel.ultrashop.models.Product;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.TypeShop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamicCalendar extends ShopType implements JsonSerializer<ShopTypeDynamicCalendar>, JsonDeserializer<ShopTypeDynamicCalendar> {
  public static ShopTypeDynamicCalendar INSTANCE = new ShopTypeDynamicCalendar();
  private List<DateRange> dateRanges;
  private int cooldown;
  private int productsRotation;

  public ShopTypeDynamicCalendar() {
    setTypeShop(TypeShop.DYNAMIC_CALENDAR);
    dateRanges = List.of(
      new DateRange(LocalDate.now(), LocalDate.now().plusWeeks(1))
    );
    cooldown = 30;
    productsRotation = 3;
  }

  public ShopTypeDynamicCalendar(List<DateRange> dateRanges) {
    setTypeShop(TypeShop.DYNAMIC_CALENDAR);
    this.dateRanges = dateRanges;
    cooldown = 30;
    productsRotation = 3;
  }

  @Override public void check() {
    setTypeShop(TypeShop.DYNAMIC_CALENDAR);
    if (dateRanges == null || dateRanges.isEmpty()) dateRanges = List.of(
      new DateRange(LocalDate.now(), LocalDate.now().plusWeeks(1))
    );
  }

  @Override public List<Product> getProducts(Shop shop, ShopOptionsApi options) {
    return ShopTypeDynamic.getDynamicProducts(shop, options);
  }

  @Override public boolean isOpen() {
    return ShopTypeCalendar.canEnterDate(dateRanges);
  }


  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(
        UltraShop.dataShop.getActualCooldown(shop, shopOptionsApi)
      ))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%amountProducts%", String.valueOf(this.getProductsRotation()));
  }

  @Override
  public JsonElement serialize(ShopTypeDynamicCalendar src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    JsonArray dateRangesArray = new JsonArray();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    for (DateRange range : src.getDateRanges()) {
      JsonObject rangeObject = new JsonObject();
      rangeObject.addProperty("startDate", range.getStartDate().format(formatter));
      rangeObject.addProperty("endDate", range.getEndDate().format(formatter));
      dateRangesArray.add(rangeObject);
    }
    jsonObject.add("dateRanges", dateRangesArray);
    jsonObject.addProperty("cooldown", src.getCooldown());
    jsonObject.addProperty("productsRotation", src.getProductsRotation());
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    return jsonObject;
  }

  @Override
  public ShopTypeDynamicCalendar deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    JsonArray dateRangesArray = jsonObject.getAsJsonArray("dateRanges");
    List<DateRange> dateRanges = new ArrayList<>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    if (dateRangesArray != null) {
      for (JsonElement rangeElement : dateRangesArray) {
        JsonObject rangeObject = rangeElement.getAsJsonObject();
        LocalDate startDate = LocalDate.parse(rangeObject.get("startDate").getAsString(), formatter);
        LocalDate endDate = LocalDate.parse(rangeObject.get("endDate").getAsString(), formatter);
        dateRanges.add(new DateRange(startDate, endDate));
      }
    } else {
      LocalDate today = LocalDate.now();
      dateRanges.add(new DateRange(today, today.plusWeeks(1)));
    }

    int cooldown = jsonObject.has("cooldown") ? jsonObject.get("cooldown").getAsInt() : 30;
    int productsRotation = jsonObject.has("productsRotation") ? jsonObject.get("productsRotation").getAsInt() : 3;

    ShopTypeDynamicCalendar shopTypeCalendar = new ShopTypeDynamicCalendar(dateRanges);
    shopTypeCalendar.setCooldown(cooldown);
    shopTypeCalendar.setProductsRotation(productsRotation);
    shopTypeCalendar.setTypeShop(TypeShop.valueOf(jsonObject.has("typeShop") ? jsonObject.get("typeShop").getAsString() : "DYNAMIC_CALENDAR"));
    return shopTypeCalendar;
  }
}