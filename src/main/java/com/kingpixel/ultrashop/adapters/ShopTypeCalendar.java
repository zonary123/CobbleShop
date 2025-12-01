package com.kingpixel.ultrashop.adapters;

import com.google.gson.*;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.models.DateRange;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.TypeShop;
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
public class ShopTypeCalendar extends ShopType implements JsonSerializer<ShopTypeCalendar>, JsonDeserializer<ShopTypeCalendar> {
  public static ShopTypeCalendar INSTANCE = new ShopTypeCalendar();
  private List<DateRange> dateRanges;

  public ShopTypeCalendar() {
    setTypeShop(TypeShop.CALENDAR);
    dateRanges = List.of(
      new DateRange(LocalDate.now(), LocalDate.now().plusWeeks(1))
    );
  }

  public ShopTypeCalendar(List<DateRange> dateRanges) {
    setTypeShop(TypeShop.CALENDAR);
    this.dateRanges = dateRanges;
  }

  @Override public void check() {
    setTypeShop(TypeShop.CALENDAR);
    if (dateRanges == null || dateRanges.isEmpty()) dateRanges = List.of(
      new DateRange(LocalDate.now(), LocalDate.now().plusWeeks(1))
    );
  }

  @Override public boolean isOpen() {
    return canEnterDate(dateRanges);
  }

  public static boolean canEnterDate(List<DateRange> dateRanges) {
    LocalDate today = LocalDate.now();
    for (DateRange range : dateRanges) {
      if (!today.isBefore(range.getStartDate()) && !today.isAfter(range.getEndDate())) {
        return true;
      }
    }
    return false;
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text;
  }

  @Override public JsonElement serialize(ShopTypeCalendar src, Type typeOfSrc, JsonSerializationContext context) {
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
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    return jsonObject;
  }

  @Override
  public ShopTypeCalendar deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
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

    ShopTypeCalendar shopTypeCalendar = new ShopTypeCalendar(dateRanges);
    shopTypeCalendar.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));
    return shopTypeCalendar;
  }


}