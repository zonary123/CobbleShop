package com.kingpixel.ultrashop.domain.service;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.Model.conditions.util.ConditionUtils;
import com.kingpixel.ultrashop.domain.model.PriceEntry;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.ShopReference;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates prices, discounts, and formatting for products.
 * Pure business logic — no GUI, no I/O.
 */
public final class PriceCalculator {

  private static final int SCALE = 5;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  private PriceCalculator() {
  }

  /**
   * Calculates total buy prices per economy for a product (multi-currency aware).
   */
  public static Map<EconomyUse, BigDecimal> getBuyPrices(Product product, ServerPlayerEntity player,
                                                          int amount, ShopReference shop, ShopConfig config) {
    Map<EconomyUse, BigDecimal> result = new LinkedHashMap<>();
    float discountPercent = getDiscount(product, player, shop, config);
    List<PriceEntry> entries = product.getEffectivePrices(shop);

    for (PriceEntry entry : entries) {
      if (!entry.isBuyable()) continue;
      BigDecimal total = entry.getBuy().multiply(BigDecimal.valueOf(amount));
      if (discountPercent > 0f) {
        BigDecimal discountFraction = BigDecimal.valueOf(discountPercent / 100.0);
        total = total.subtract(total.multiply(discountFraction));
      }
      result.put(entry.getEconomy(), total.setScale(SCALE, ROUNDING));
    }
    return result;
  }

  /**
   * Calculates total sell prices per economy for a product (multi-currency aware).
   */
  public static Map<EconomyUse, BigDecimal> getSellPrices(Product product, int amount, ShopReference shop) {
    Map<EconomyUse, BigDecimal> result = new LinkedHashMap<>();
    List<PriceEntry> entries = product.getEffectivePrices(shop);

    for (PriceEntry entry : entries) {
      if (!entry.isSellable()) continue;
      BigDecimal total = entry.getSell().multiply(BigDecimal.valueOf(amount));
      result.put(entry.getEconomy(), total.setScale(SCALE, ROUNDING));
    }
    return result;
  }

  /**
   * Calculates sell price per unit per economy, accounting for pack sizes.
   */
  public static Map<EconomyUse, BigDecimal> getSellPricesPerUnit(Product product, ShopReference shop) {
    Map<EconomyUse, BigDecimal> result = new LinkedHashMap<>();
    int packSize = product.getItemStack().getCount();
    List<PriceEntry> entries = product.getEffectivePrices(shop);

    for (PriceEntry entry : entries) {
      if (!entry.isSellable()) continue;
      BigDecimal perUnit = packSize <= 1
        ? entry.getSell()
        : entry.getSell().divide(BigDecimal.valueOf(packSize), SCALE, ROUNDING);
      result.put(entry.getEconomy(), perUnit);
    }
    return result;
  }

  // --- Legacy single-value methods (for backward compat in simple cases) ---

  /**
   * Calculates the total buy price (first economy only — simple mode).
   */
  public static BigDecimal getBuyPrice(Product product, ServerPlayerEntity player, int amount,
                                       ShopReference shop, ShopConfig config) {
    Map<EconomyUse, BigDecimal> prices = getBuyPrices(product, player, amount, shop, config);
    return prices.values().stream().findFirst().orElse(BigDecimal.ZERO);
  }

  /**
   * Calculates the total sell price (first economy only — simple mode).
   */
  public static BigDecimal getSellPrice(Product product, int amount, ShopReference shop) {
    Map<EconomyUse, BigDecimal> prices = getSellPrices(product, amount, shop);
    return prices.values().stream().findFirst().orElse(BigDecimal.ZERO);
  }

  /**
   * Calculates the sell price per unit (first economy only — simple mode).
   */
  public static BigDecimal getSellPricePerUnit(Product product, ShopReference shop) {
    Map<EconomyUse, BigDecimal> prices = getSellPricesPerUnit(product, shop);
    return prices.values().stream().findFirst().orElse(BigDecimal.ZERO);
  }

  /**
   * Calculates the best applicable discount for a player.
   */
  public static float getDiscount(Product product, ServerPlayerEntity player, ShopReference shop, ShopConfig config) {
    float result = 0.0f;
    result = findBestPermissionDiscount(shop.getDiscounts(), player, result);
    if (config != null) {
      result = findBestPermissionDiscount(config.getDiscounts(), player, result);
    }
    if (shop.getGlobalDiscount() <= 0f) {
      float productDiscount = product.getDiscount() != null ? product.getDiscount() : 0f;
      if (productDiscount > result) result = productDiscount;
    } else {
      if (shop.getGlobalDiscount() > result) result = shop.getGlobalDiscount();
    }
    return result;
  }

  /**
   * Whether a product can be sold by a player in a specific shop context.
   */
  public static boolean canSell(Product product, ServerPlayerEntity player, ShopReference shop, ShopConfig config) {
    if (!product.canBeSold()) return false;
    if (player != null) {
      BigDecimal buyPrice = getBuyPrice(product, player, 1, shop, config);
      BigDecimal sellPrice = getSellPricePerUnit(product, shop);
      return buyPrice.compareTo(BigDecimal.ZERO) <= 0 || buyPrice.compareTo(sellPrice) >= 0;
    }
    return true;
  }

  /**
   * Checks if a player meets the permission/condition requirements to interact with a product.
   */
  public static boolean hasPermission(Product product, ServerPlayerEntity player) {
    if (product.getConditions() != null && !product.getConditions().isEmpty()) {
      return ConditionUtils.check(product.getConditions(), player);
    }
    if (product.getNotBuyPermission() != null && PermissionApi.hasPermission(player, product.getNotBuyPermission(), 4)) {
      return false;
    }
    return product.getCanBuyPermission() == null || PermissionApi.hasPermission(player, product.getCanBuyPermission(), 4);
  }

  private static float findBestPermissionDiscount(Map<String, Float> discounts, ServerPlayerEntity player, float current) {
    if (discounts == null || discounts.isEmpty()) return current;
    for (Map.Entry<String, Float> entry : discounts.entrySet()) {
      if (entry.getValue() > current && PermissionApi.hasPermission(player, entry.getKey(), 4)) {
        current = entry.getValue();
      }
    }
    return current;
  }
}

