package com.kingpixel.cobbleshop.adapters;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import lombok.Data;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:23
 */
@Data
public class ShopTypeWeekly extends ShopType {
  public static ShopTypeWeekly INSTANCE = new ShopTypeWeekly();
  private final List<DayOfWeek> days;

  public ShopTypeWeekly() {
    days = Arrays.stream(DayOfWeek.values()).toList();
  }

  public ShopTypeWeekly(List<DayOfWeek> days) {
    this.days = days;
  }

  @Override public boolean isOpen() {
    return days.contains(DayOfWeek.from(LocalDate.now()));
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    String[] days = getDays().stream().map(DayOfWeek::toString).toArray(String[]::new);
    return text
      .replace("%days%", String.join(", ", days));
  }

  // JSON
  @Override public void write(JsonWriter out, ShopType value) throws IOException {
    out.beginArray();
    for (DayOfWeek day : days) {
      out.value(day.toString());
    }
    out.endArray();
  }

  @Override public ShopType read(JsonReader in) throws IOException {
    in.beginArray();
    while (in.hasNext()) {
      days.add(DayOfWeek.valueOf(in.nextString()));
    }
    in.endArray();
    return this;
  }
}
