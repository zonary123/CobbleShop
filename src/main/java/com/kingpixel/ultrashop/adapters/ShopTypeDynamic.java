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
import java.util.List;

/**
 * Author: Carlos Varas Alonso - 21/02/2025 5:23
 */
@EqualsAndHashCode(callSuper = true) @Data
public class ShopTypeDynamic extends ShopType implements JsonSerializer<ShopTypeDynamic>, JsonDeserializer<ShopTypeDynamic> {
  public static ShopTypeDynamic INSTANCE = new ShopTypeDynamic();
  private DurationValue cooldown;
  private int productsRotation;

  public ShopTypeDynamic() {
    setTypeShop(TypeShop.DYNAMIC);
    cooldown = DurationValue.parse("30m");
    productsRotation = 3;
  }

  public ShopTypeDynamic(DurationValue cooldown, int productsRotation) {
    setTypeShop(TypeShop.DYNAMIC);
    this.cooldown = cooldown;
    this.productsRotation = productsRotation;
  }

  @Override public void check() {
    setTypeShop(TypeShop.DYNAMIC);
    if (cooldown == null) cooldown = DurationValue.parse("30m");
    productsRotation = Math.max(1, productsRotation);
  }

  @Override public List<Product> getProducts(Shop shop, ShopOptionsApi options) {
    return getDynamicProducts(shop, options);
  }

  public static List<Product> getDynamicProducts(Shop shop, ShopOptionsApi options) {
    return UltraShop.dataShop.updateDynamicProducts(shop, options);
  }

  @Override public String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi) {
    return text
      .replace("%cooldown%", PlayerUtils.getCooldown(
        UltraShop.dataShop.getActualCooldown(shop, shopOptionsApi)
      ))
      .replace("%number%", String.valueOf(this.getProductsRotation()))
      .replace("%amountProducts%", String.valueOf(this.getProductsRotation()));
  }

  // JSON

  @Override public JsonElement serialize(ShopTypeDynamic src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("typeShop", src.getTypeShop().toString());
    jsonObject.add("cooldown", DurationValue.INSTANCE.serialize(src.getCooldown(), DurationValue.class, context));
    jsonObject.addProperty("productsRotation", src.getProductsRotation());
    return jsonObject;
  }

  @Override
  public ShopTypeDynamic deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    JsonObject jsonObject = json.getAsJsonObject();

    JsonElement jsonCooldown = jsonObject.get("cooldown");
    DurationValue cooldown = (jsonCooldown != null)
      ? DurationValue.INSTANCE.deserialize(jsonCooldown, DurationValue.class, context)
      : DurationValue.parse("30m");

    JsonElement jsonProductsRotation = jsonObject.get("productsRotation");
    int productsRotation = (jsonProductsRotation != null) ? jsonProductsRotation.getAsInt() : 3; // Default value 3

    ShopTypeDynamic shopTypeDynamic = new ShopTypeDynamic(cooldown, productsRotation);
    shopTypeDynamic.setTypeShop(TypeShop.valueOf(jsonObject.get("typeShop").getAsString()));

    return shopTypeDynamic;
  }
}