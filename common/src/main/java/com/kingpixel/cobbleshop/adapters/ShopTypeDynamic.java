package com.kingpixel.cobbleshop.adapters;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:23
 */
@Data
public class ShopTypeDynamic extends ShopType {
  public static ShopTypeDynamic INSTANCE = new ShopTypeDynamic();
  private int cooldown;
  private int productsRotation;

  public ShopTypeDynamic() {
  }

  public ShopTypeDynamic(int cooldown, int productsRotation) {
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(this.getCooldown()))))
      .replace("%number%", String.valueOf(this.getProductsRotation()));

  }

  // JSON
  @Override public void write(JsonWriter out, ShopType value) throws IOException {
    out.beginObject();
    out.name("cooldown").value(cooldown);
    out.name("productsRotation").value(productsRotation);
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
      }
    }
    in.endObject();
    return this;
  }
}
