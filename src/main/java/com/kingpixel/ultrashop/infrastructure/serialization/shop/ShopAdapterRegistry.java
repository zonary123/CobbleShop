package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.shop.Shop;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of {@link ShopJsonAdapter} instances keyed by {@link ShopType}.
 *
 * <p>Allows extension of the (de)serialization layer WITHOUT modifying any
 * existing class — a third-party plugin adds a new shop type by:</p>
 * <ol>
 *   <li>Implementing the new {@code Shop} subtype and adding it to {@code ShopVisitor}.</li>
 *   <li>Implementing a matching {@link ShopJsonAdapter}.</li>
 *   <li>Calling {@link #register(ShopType, ShopJsonAdapter)} at boot.</li>
 * </ol>
 *
 * <p>This is the Open/Closed Principle made literal at the serialization layer.</p>
 */
public final class ShopAdapterRegistry {

  private final Map<ShopType, ShopJsonAdapter<? extends Shop>> adapters =
    new EnumMap<>(ShopType.class);

  public ShopAdapterRegistry() {
    register(ShopType.NORMAL, new NormalShopAdapter());
    register(ShopType.CATEGORY, new CategoryShopAdapter());
    register(ShopType.ROTATION, new RotationShopAdapter());
  }

  public <S extends Shop> void register(ShopType type, ShopJsonAdapter<S> adapter) {
    adapters.put(type, adapter);
  }

  @SuppressWarnings("unchecked")
  public ShopJsonAdapter<Shop> resolve(ShopType type) {
    ShopJsonAdapter<?> adapter = adapters.get(type);
    if (adapter == null) {
      throw new IllegalStateException("No ShopJsonAdapter registered for type: " + type);
    }
    return (ShopJsonAdapter<Shop>) adapter;
  }
}

