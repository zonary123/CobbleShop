package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.reflect.TypeToken;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Gson adapter for {@link CategoryShop}. Writes the four config VOs and the
 * sub-shop references — never any products at this level.
 */
public final class CategoryShopAdapter implements ShopJsonAdapter<CategoryShop> {

  private static final Type SUBSHOP_LIST = new TypeToken<List<SubShop>>() {}.getType();

  @Override
  public JsonObject serialize(CategoryShop shop, JsonSerializationContext ctx) {
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
    obj.add("subShops", ctx.serialize(shop.getSubShops(), SUBSHOP_LIST));
    return obj;
  }

  @Override
  public CategoryShop deserialize(JsonObject json, JsonDeserializationContext ctx) {
    CategoryShop shop = new CategoryShop();
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
    if (json.has("subShops")) {
      shop.setSubShops(ctx.deserialize(json.get("subShops"), SUBSHOP_LIST));
    }
    return shop;
  }
}

