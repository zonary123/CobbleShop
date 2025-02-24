package com.kingpixel.cobbleshop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypePermanent extends ShopType implements JsonSerializer<ShopTypePermanent>, JsonDeserializer<ShopTypePermanent> {
  public static ShopTypePermanent INSTANCE = new ShopTypePermanent();

  public ShopTypePermanent() {
    setTypeShop(TypeShop.PERMANENT);
  }

  @Override public void check() {
    setTypeShop(TypeShop.PERMANENT);
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return "";
  }

  // JSON

  @Override public JsonElement serialize(ShopTypePermanent src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    return jsonObject;
  }

  @Override
  public ShopTypePermanent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    ShopTypePermanent shopTypePermanent = new ShopTypePermanent();
    shopTypePermanent.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));
    return shopTypePermanent;
  }
}