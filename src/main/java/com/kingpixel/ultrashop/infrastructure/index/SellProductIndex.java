package com.kingpixel.ultrashop.infrastructure.index;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.service.PriceCalculator;
import com.kingpixel.ultrashop.domain.service.ProductMatcher;
import com.kingpixel.ultrashop.presentation.gui.ShopProducts;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
   * Key: item registry id (e.g. "minecraft:stone") → pre-sorted immutable entries.
   */
  private final Map<String, List<SellEntry>> index = new ConcurrentHashMap<>();

  /**
   * Rebuild the entire index from all loaded shops.
   */
  public void rebuild(Map<String, List<Shop>> allShops) {
    Map<String, List<SellEntry>> nextIndex = new ConcurrentHashMap<>();

    allShops.forEach((modId, shops) -> {
      for (Shop shop : shops) {
        List<Product> products = getActiveProducts(shop, modId);
        for (Product product : products) {
          if (!product.canBeSold()) {
            continue;
          }

          ItemStack stack = product.getItemStack();
          if (stack.isEmpty()) {
            continue;
          }

          Identifier itemId = Registries.ITEM.getId(stack.getItem());
          String key = itemId.toString();
          BigDecimal sellPricePerUnit = PriceCalculator.getSellPricePerUnit(product, shop);

          nextIndex.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new SellEntry(shop, product, modId, stack.copy(), sellPricePerUnit));
        }
      }
    });

    nextIndex.replaceAll((key, entries) -> entries.stream()
      .sorted(Comparator.comparing(SellEntry::sellPricePerUnit).reversed())
      .toList());

    index.clear();
    index.putAll(nextIndex);
  }

  /**
   * Find all sellable entries for a given item stack.
   * Returns entries sorted by best price (highest first).
   */
  public List<SellEntry> findSellable(ItemStack itemStack, ServerPlayerEntity player) {
    if (itemStack == null || itemStack.isEmpty()) {
      return List.of();
    }

    Identifier itemId = Registries.ITEM.getId(itemStack.getItem());
    String key = itemId.toString();
    List<SellEntry> candidates = index.getOrDefault(key, List.of());

    if (candidates.isEmpty()) {
      return List.of();
    }

    ShopContext ctx = ShopContext.get();
    List<SellEntry> matching = new ArrayList<>();
    for (SellEntry entry : candidates) {
      // Full NBT/component match
      if (!ProductMatcher.matches(itemStack, entry.templateStack())) {
        continue;
      }

      // Check if product can actually be sold in context
      com.kingpixel.ultrashop.infrastructure.config.ShopConfig config = ctx.getConfigs().get(entry.modId());
      if (config != null && PriceCalculator.canSell(entry.product(), player, entry.shop(), config)) {
        matching.add(entry);
      }
    }

    return matching;
  }

  private List<Product> getActiveProducts(Shop shop, String modId) {
    return ShopProducts.activeProducts(shop, modId);
  }

  /**
   * An entry in the sell index: a product and the shop it belongs to.
   */
  public record SellEntry(Shop shop, Product product, String modId,
                          ItemStack templateStack, BigDecimal sellPricePerUnit) {
  }
}
