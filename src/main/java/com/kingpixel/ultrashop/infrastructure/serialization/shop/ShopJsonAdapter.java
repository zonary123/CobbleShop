package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.kingpixel.ultrashop.domain.model.shop.Shop;

/**
 * Per-{@code ShopType} JSON adapter. Each implementation knows how to write
 * exactly one concrete shop subtype to JSON and read it back.
 *
 * <p>Adapters are looked up via {@link ShopAdapterRegistry} and dispatched by
 * {@link ShopTypeAdapterFactory} based on the JSON {@code "type"} discriminator
 * on read, or {@link Shop#getType()} on write.</p>
 *
 * @param <S> the concrete shop subtype this adapter handles
 */
public interface ShopJsonAdapter<S extends Shop> {

  /** Writes the shop to a JSON object. The {@code "type"} field is added by the caller. */
  JsonObject serialize(S shop, JsonSerializationContext ctx);

  /** Reads the shop from a JSON object. The {@code "type"} field has already been validated. */
  S deserialize(JsonObject json, JsonDeserializationContext ctx);
}

