package com.kingpixel.cobbleshop.adapters;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import lombok.Data;

import java.io.IOException;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:23
 */
@Data
public class ShopTypePermanent extends ShopType {
  public static ShopTypePermanent INSTANCE = new ShopTypePermanent();

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return "";
  }

  @Override public void write(JsonWriter out, ShopType value) throws IOException {
  }

  @Override public ShopType read(JsonReader in) throws IOException {
    return null;
  }


}
