package com.kingpixel.ultrashop.infrastructure.index;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.service.PriceCalculator;
import com.kingpixel.ultrashop.domain.service.ProductMatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-indexed sell product lookup — O(1) per item instead of O(shops × products).
 *
 * <p>Rebuilt whenever shops are loaded or dynamic products rotate.</p>
 */
public class SellProductIndex {

  /**
   * Key: item registry id (e.g. "minecraft:stone") → List of (Shop, Product) pairs.
   */
  private final Map<String, List<SellEntry>> index = new ConcurrentHashMap<>();

  /**
   * Rebuild the entire index from all loaded shops.
   */
  public void rebuild(Map<String, List<Shop>> allShops) {
    index.clear();
    allShops.forEach((modId, shops) -> {
      for (Shop shop : shops) {
        List<Product> products = getActiveProducts(shop, modId);
        for (Product product : products) {
          if (!product.canBeSold()) continue;

          ItemStack stack = product.getItemStack();
          if (stack.isEmpty()) continue;

          Identifier itemId = Registries.ITEM.getId(stack.getItem());
          String key = itemId.toString();
          index.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new SellEntry(shop, product, modId));
        }
      }
    });
  }

  /**
   * Find all sellable entries for a given item stack.
   * Returns entries sorted by best price (highest first).
   */
  public List<SellEntry> findSellable(ItemStack itemStack, ServerPlayerEntity player) {
    if (itemStack == null || itemStack.isEmpty()) return List.of();

    Identifier itemId = Registries.ITEM.getId(itemStack.getItem());
    String key = itemId.toString();
    List<SellEntry> candidates = index.getOrDefault(key, List.of());

    if (candidates.isEmpty()) return List.of();

    ShopContext ctx = ShopContext.get();
    List<SellEntry> matching = new ArrayList<>();
    for (SellEntry entry : candidates) {
      // Full NBT/component match
      if (!ProductMatcher.matches(itemStack, entry.product().getItemStack())) continue;

      // Check if product can actually be sold in context
      var config = ctx.getConfigs().get(entry.modId());
      if (config != null && PriceCalculator.canSell(entry.product(), player, entry.shop(), config)) {
        matching.add(entry);
      }
    }

    // Sort by sell price descending (best price first)
    matching.sort((a, b) -> PriceCalculator.getSellPricePerUnit(b.product(), b.shop())
      .compareTo(PriceCalculator.getSellPricePerUnit(a.product(), a.shop())));

    return matching;
  }

  private List<Product> getActiveProducts(Shop shop, String modId) {
    if (shop.getRotationSchedule() != null) {
      ShopContext ctx = ShopContext.get();
      return ctx.getDataShop().updateDynamicProducts(shop, modId, false);
    }
    return shop.getProducts();
  }

  /**
   * An entry in the sell index: a product and the shop it belongs to.
   */
  public record SellEntry(Shop shop, Product product, String modId) {
  }
}

