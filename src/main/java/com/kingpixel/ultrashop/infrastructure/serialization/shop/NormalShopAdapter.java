package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Gson adapter for {@link NormalShop}. Writes the four config VOs and the
 * static product catalog as separate sub-objects.
 */
public final class NormalShopAdapter implements ShopJsonAdapter<NormalShop> {

  private static final Type PRODUCT_LIST = new TypeToken<List<Product>>() {}.getType();

  @Override
  public JsonObject serialize(NormalShop shop, JsonSerializationContext ctx) {
    JsonObject obj = new JsonObject();
    obj.addProperty("id", shop.getId());
    obj.addProperty("maintenance", shop.isMaintenance());
    if (shop.getWebhookUrl() != null) {
      obj.addProperty("webhookUrl", shop.getWebhookUrl());
    }
    obj.add("displayConfig", ctx.serialize(shop.getDisplayConfig(), DisplayConfig.class));
    obj.add("economyConfig", ctx.serialize(shop.getEconomyConfig(), EconomyConfig.class));
    obj.add("conditionsConfig", ctx.serialize(shop.getConditionsConfig(), ConditionsConfig.class));
    obj.add("soundConfig", ctx.serialize(shop.getSoundConfig(), SoundConfig.class));
    obj.add("products", ctx.serialize(shop.getProducts(), PRODUCT_LIST));
    if (shop.getDailySellLimits() != null && !shop.getDailySellLimits().isEmpty()) {
      obj.add("dailySellLimits", ctx.serialize(shop.getDailySellLimits(), new TypeToken<Map<String, BigDecimal>>(){}.getType()));
    }
    if (shop.getDailySellResetCooldown() != null) {
      obj.addProperty("dailySellResetCooldown", shop.getDailySellResetCooldown());
    }
    return obj;
  }
 
  @Override
  public NormalShop deserialize(JsonObject json, JsonDeserializationContext ctx) {
    NormalShop shop = new NormalShop();
    if (json.has("id") && !json.get("id").isJsonNull()) {
      shop.setId(json.get("id").getAsString());
    }
    if (json.has("maintenance") && !json.get("maintenance").isJsonNull()) {
      shop.setMaintenance(json.get("maintenance").getAsBoolean());
    }
    if (json.has("webhookUrl") && !json.get("webhookUrl").isJsonNull()) {
      shop.setWebhookUrl(json.get("webhookUrl").getAsString());
    }
    shop.setDisplayConfig(ctx.deserialize(json.get("displayConfig"), DisplayConfig.class));
    shop.setEconomyConfig(ctx.deserialize(json.get("economyConfig"), EconomyConfig.class));
    shop.setConditionsConfig(ctx.deserialize(json.get("conditionsConfig"), ConditionsConfig.class));
    shop.setSoundConfig(ctx.deserialize(json.get("soundConfig"), SoundConfig.class));
    if (json.has("products")) {
      shop.setProducts(ctx.deserialize(json.get("products"), PRODUCT_LIST));
    }
    if (json.has("dailySellLimits")) {
      shop.setDailySellLimits(ctx.deserialize(json.get("dailySellLimits"), new TypeToken<Map<String, BigDecimal>>(){}.getType()));
    }
    if (json.has("dailySellResetCooldown") && !json.get("dailySellResetCooldown").isJsonNull()) {
      shop.setDailySellResetCooldown(json.get("dailySellResetCooldown").getAsString());
    }
    return shop;
  }
}

