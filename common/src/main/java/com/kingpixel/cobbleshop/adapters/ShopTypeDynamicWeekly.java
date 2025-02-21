package com.kingpixel.cobbleshop.adapters;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:23
 */
@Data
public class ShopTypeDynamicWeekly extends ShopType {
  public static ShopTypeDynamicWeekly INSTANCE = new ShopTypeDynamicWeekly();
  private int cooldown;
  private int productsRotation;
  private final List<DayOfWeek> days;

  public ShopTypeDynamicWeekly() {
    days = Arrays.stream(DayOfWeek.values()).toList();
    cooldown = 30;
    productsRotation = 3;
  }

  public ShopTypeDynamicWeekly(int cooldown, int productsRotation, List<DayOfWeek> days) {
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
    this.days = days;
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    String[] days = getDays().stream().map(DayOfWeek::toString).toArray(String[]::new);
    return text
      .replace("%cooldown%",
        PlayerUtils.getCooldown(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ShopApi.getCooldown(shop)))))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%days%", String.join(", ", days));
  }

  @Override public void write(JsonWriter out, ShopType value) throws IOException {
    out.beginObject();
    out.name("cooldown").value(cooldown);
    out.name("productsRotation").value(productsRotation);
    out.name("days");
    out.beginArray();
    for (DayOfWeek day : days) {
      out.value(day.toString());
    }
    out.endArray();
    out.endObject();
  }

  @Override public ShopType read(JsonReader in) throws IOException {
    in.beginObject();
    while (in.hasNext()) {
      String name = in.nextName();
      switch (name) {
        case "cooldown":
          cooldown = in.nextInt();
          break;
        case "productsRotation":
          productsRotation = in.nextInt();
          break;
        case "days":
          in.beginArray();
          while (in.hasNext()) {
            days.add(DayOfWeek.valueOf(in.nextString()));
          }
          in.endArray();
          break;
      }
    }
    return this;
  }


}
