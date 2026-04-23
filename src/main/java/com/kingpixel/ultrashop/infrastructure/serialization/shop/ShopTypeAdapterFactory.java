package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopBridge;

import java.lang.reflect.Type;

/**
 * Top-level Gson (de)serializer that dispatches to the right
 * {@link ShopJsonAdapter} based on the {@code "type"} discriminator.
 *
 * <p><b>Output</b> always uses the new format:</p>
 * <pre>{@code
 * { "type": "ROTATION", "id": "daily", "displayConfig": {...}, "scheduler": {...}, ... }
 * }</pre>
 *
 * <p><b>Input</b> handles three shapes (backwards-compat / data-loss prevention):</p>
 * <ol>
 *   <li><b>New format</b>: explicit {@code "type"} discriminator + {@code displayConfig}
 *       sub-object → dispatched to the matching {@link ShopJsonAdapter}.</li>
 *   <li><b>Legacy format</b>: flat {@code Shop} fields ({@code name}, {@code title},
 *       {@code products}, {@code subShops}, {@code rotationSchedule}, ...) →
 *       deserialized via the legacy class and bridged via
 *       {@link ShopBridge#fromLegacy(com.kingpixel.ultrashop.domain.model.Shop)}.
 *       This is what makes existing production configs load WITHOUT manual migration.</li>
 *   <li><b>Empty / malformed</b> → throws {@link JsonParseException} (the caller
 *       owns the decision to skip the file or default it).</li>
 * </ol>
 */
public final class ShopTypeAdapterFactory implements JsonSerializer<Shop>, JsonDeserializer<Shop> {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_DISPLAY_CONFIG = "displayConfig";

  private final ShopAdapterRegistry registry;

  public ShopTypeAdapterFactory() {
    this(new ShopAdapterRegistry());
  }

  public ShopTypeAdapterFactory(ShopAdapterRegistry registry) {
    this.registry = registry;
  }

  // --- Serialize ----------------------------------------------------------

  @Override
  public JsonElement serialize(Shop src, Type typeOfSrc, JsonSerializationContext ctx) {
    JsonObject json = registry.resolve(src.getType()).serialize(src, ctx);
    json.addProperty(FIELD_TYPE, src.getType().name());
    return json;
  }

  // --- Deserialize --------------------------------------------------------

  @Override
  public Shop deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
    if (json == null || !json.isJsonObject()) {
      throw new JsonParseException("Shop JSON must be an object, got: " + json);
    }
    JsonObject obj = json.getAsJsonObject();

    if (isNewFormat(obj)) {
      return readNewFormat(obj, ctx);
    }
    return readLegacyFormat(obj, ctx);
  }

  private boolean isNewFormat(JsonObject obj) {
    // The new format ALWAYS carries a sub-object 'displayConfig'. Legacy shops
    // had a flat 'name' / 'title' at the root and an ItemModel field literally
    // named 'display' — never a 'displayConfig' object.
    return obj.has(FIELD_DISPLAY_CONFIG) && obj.get(FIELD_DISPLAY_CONFIG).isJsonObject();
  }

  private Shop readNewFormat(JsonObject obj, JsonDeserializationContext ctx) {
    if (!obj.has(FIELD_TYPE)) {
      throw new JsonParseException("New-format shop JSON missing 'type' discriminator");
    }
    ShopType type;
    try {
      type = ShopType.valueOf(obj.get(FIELD_TYPE).getAsString());
    } catch (IllegalArgumentException e) {
      throw new JsonParseException("Unknown shop type: " + obj.get(FIELD_TYPE), e);
    }
    return registry.resolve(type).deserialize(obj, ctx);
  }

  private Shop readLegacyFormat(JsonObject obj, JsonDeserializationContext ctx) {
    UltraShop.LOGGER.debug("Detected legacy shop JSON format — bridging to new hierarchy.");
    com.kingpixel.ultrashop.domain.model.Shop legacy =
      ctx.deserialize(obj, com.kingpixel.ultrashop.domain.model.Shop.class);
    if (legacy == null) {
      throw new JsonParseException("Failed to deserialize legacy shop");
    }
    legacy.check(); // populate defaults / auto-promote type
    return ShopBridge.fromLegacy(legacy);
  }
}

