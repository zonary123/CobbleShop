package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.RotationScope;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Gson adapter for {@link RotationShop}. Writes the four config VOs, the
 * polymorphic {@link Scheduler}, the rotation amount, and the product pool.
 */
public final class RotationShopAdapter implements ShopJsonAdapter<RotationShop> {

  private static final Type PRODUCT_LIST = new TypeToken<List<Product>>() {}.getType();

  @Override
  public JsonObject serialize(RotationShop shop, JsonSerializationContext ctx) {
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
    obj.add("scheduler", ctx.serialize(shop.getScheduler(), Scheduler.class));
    obj.addProperty("rotationAmount", shop.getRotationAmount());
    if (shop.getRotationScope() != null && shop.getRotationScope() != RotationScope.GLOBAL) {
      obj.addProperty("rotationScope", shop.getRotationScope().name());
    }
    if (shop.getRotationSlots() != null && !shop.getRotationSlots().isEmpty()) {
      obj.add("rotationSlots", ctx.serialize(shop.getRotationSlots(), new TypeToken<List<Integer>>(){}.getType()));
    }
    obj.add("productPool", ctx.serialize(shop.getProductPool(), PRODUCT_LIST));
    if (shop.getDailySellLimits() != null && !shop.getDailySellLimits().isEmpty()) {
      obj.add("dailySellLimits", ctx.serialize(shop.getDailySellLimits(), new TypeToken<Map<String, BigDecimal>>(){}.getType()));
    }
    if (shop.getDailySellResetCooldown() != null) {
      obj.addProperty("dailySellResetCooldown", shop.getDailySellResetCooldown());
    }
    return obj;
  }

  @Override
  public RotationShop deserialize(JsonObject json, JsonDeserializationContext ctx) {
    RotationShop shop = new RotationShop();
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
    shop.setScheduler(ctx.deserialize(json.get("scheduler"), Scheduler.class));
    if (json.has("rotationAmount") && !json.get("rotationAmount").isJsonNull()) {
      shop.setRotationAmount(json.get("rotationAmount").getAsInt());
    }
    if (json.has("rotationScope") && !json.get("rotationScope").isJsonNull()) {
      shop.setRotationScope(RotationScope.valueOf(json.get("rotationScope").getAsString().toUpperCase()));
    }
    if (json.has("rotationSlots") && !json.get("rotationSlots").isJsonNull()) {
      shop.setRotationSlots(ctx.deserialize(json.get("rotationSlots"), new TypeToken<List<Integer>>(){}.getType()));
    }
    if (json.has("productPool")) {
      shop.setProductPool(ctx.deserialize(json.get("productPool"), PRODUCT_LIST));
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

