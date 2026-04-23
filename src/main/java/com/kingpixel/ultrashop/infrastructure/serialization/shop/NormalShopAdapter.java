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
import java.util.List;

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
    obj.add("displayConfig", ctx.serialize(shop.getDisplayConfig(), DisplayConfig.class));
    obj.add("economyConfig", ctx.serialize(shop.getEconomyConfig(), EconomyConfig.class));
    obj.add("conditionsConfig", ctx.serialize(shop.getConditionsConfig(), ConditionsConfig.class));
    obj.add("soundConfig", ctx.serialize(shop.getSoundConfig(), SoundConfig.class));
    obj.add("products", ctx.serialize(shop.getProducts(), PRODUCT_LIST));
    return obj;
  }

  @Override
  public NormalShop deserialize(JsonObject json, JsonDeserializationContext ctx) {
    NormalShop shop = new NormalShop();
    if (json.has("id") && !json.get("id").isJsonNull()) {
      shop.setId(json.get("id").getAsString());
    }
    shop.setDisplayConfig(ctx.deserialize(json.get("displayConfig"), DisplayConfig.class));
    shop.setEconomyConfig(ctx.deserialize(json.get("economyConfig"), EconomyConfig.class));
    shop.setConditionsConfig(ctx.deserialize(json.get("conditionsConfig"), ConditionsConfig.class));
    shop.setSoundConfig(ctx.deserialize(json.get("soundConfig"), SoundConfig.class));
    if (json.has("products")) {
      shop.setProducts(ctx.deserialize(json.get("products"), PRODUCT_LIST));
    }
    return shop;
  }
}

