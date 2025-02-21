package com.kingpixel.cobbleshop.adapters;

import com.google.gson.*;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamic extends ShopType implements JsonSerializer<ShopTypeDynamic>, JsonDeserializer<ShopTypeDynamic> {
  public static ShopTypeDynamic INSTANCE = new ShopTypeDynamic();
  private int cooldown;
  private int productsRotation;

  public ShopTypeDynamic() {
    setTypeShop(TypeShop.DYNAMIC);
    cooldown = 30;
    productsRotation = 3;
  }

  public ShopTypeDynamic(int cooldown, int productsRotation) {
    setTypeShop(TypeShop.DYNAMIC);
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(this.getCooldown()))))
      .replace("%number%", String.valueOf(this.getProductsRotation()));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeDynamic src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    jsonObject.addProperty("cooldown", src.getCooldown());
    jsonObject.addProperty("productsRotation", src.getProductsRotation());
    return jsonObject;
  }

  @Override
  public ShopTypeDynamic deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();
    int cooldown = jsonObject.get("cooldown").getAsInt();
    int productsRotation = jsonObject.get("productsRotation").getAsInt();
    ShopTypeDynamic shopTypeDynamic = new ShopTypeDynamic(cooldown, productsRotation);
    shopTypeDynamic.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));
    return shopTypeDynamic;
  }
}