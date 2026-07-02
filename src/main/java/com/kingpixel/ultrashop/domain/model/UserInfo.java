package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.ScheduleValue;
import com.kingpixel.cobbleutils.Model.DurationValue;
import java.time.Instant;
import java.time.ZoneId;
import lombok.Data;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * User data — buy limits, cooldowns. Pure POJO, no I/O.
 */
@Data
public class UserInfo {
  private UUID uuid;
  private String name;
  private Map<UUID, ProductLimit> cooldownProduct = new HashMap<>();
  private Map<String, BigDecimal> dailySellEarnings = new HashMap<>();
  private long dailySellReset = 0L;
  private Map<UUID, ProductLimit> cooldownProductSell = new HashMap<>();
  private Map<String, Map<String, BigDecimal>> shopDailySellEarnings = new HashMap<>();
  private Map<String, Long> shopDailySellReset = new HashMap<>();
  /** Per-shop dynamic rotation state when the shop uses {@code rotationScope: PLAYER}. */
  private Map<String, DynamicRotation> rotationShops = new HashMap<>();

  public UserInfo() {
  }

  public UserInfo(UUID uuid, String name) {
    this.uuid = uuid;
    this.name = name;
  }

  private static ScheduleValue parseCooldown(String cooldownStr) {
    if (cooldownStr == null || cooldownStr.isBlank()) {
      return ScheduleValue.ofDuration(DurationValue.parse("60m"));
    }
    cooldownStr = cooldownStr.trim();
    if (cooldownStr.contains(" ") || cooldownStr.contains("*")) {
      return ScheduleValue.ofCron(cooldownStr, ZoneId.systemDefault().getId());
    } else {
      if (cooldownStr.matches("\\d+")) {
        cooldownStr += "m";
      }
      return ScheduleValue.ofDuration(DurationValue.parse(cooldownStr));
    }
  }

  /**
   * Returns the current buy count for a product.
   */
  public int getActualProductLimit(Product product) {
    if (product.getUuid() == null || product.getMax() == null) return 0;
    ProductLimit limit = cooldownProduct.get(product.getUuid());
    if (limit == null) return 0;
    if (limit.getCooldown() <= System.currentTimeMillis()) {
      cooldownProduct.remove(product.getUuid());
      return 0;
    }
    return limit.getAmount();
  }

  /**
   * Adds a purchase to the product limit tracker.
   */
  public void addProductLimit(Product product, int amount) {
    if (product.getUuid() == null || product.getMax() == null || product.getCooldown() == null) return;
    ProductLimit limit = cooldownProduct.computeIfAbsent(product.getUuid(), k -> {
      ProductLimit pl = new ProductLimit();
      pl.setUuid(product.getUuid());
      long expiration = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60);
      try {
        expiration = parseCooldown(product.getCooldown()).toNextEpochMillis(Instant.now());
      } catch (Exception ignored) {
      }
      pl.setCooldown(expiration);
      pl.setAmount(0);
      return pl;
    });
    limit.setAmount(limit.getAmount() + amount);
  }

  /**
   * Whether the player can buy a limited product.
   */
  public boolean canBuy(Product product) {
    if (product.getUuid() == null || product.getMax() == null) return true;

    ProductLimit limit = cooldownProduct.get(product.getUuid());
    if (limit == null) return true;

    boolean onCooldown = limit.getCooldown() > System.currentTimeMillis();
    if (!onCooldown) {
      cooldownProduct.remove(product.getUuid());
      return true;
    }

    return limit.getAmount() < product.getMax();
  }

  /**
   * Returns the cooldown expiration time for a product.
   */
  public long getProductCooldown(Product product) {
    if (product.getUuid() == null) return System.currentTimeMillis();
    ProductLimit limit = cooldownProduct.get(product.getUuid());
    return limit == null ? System.currentTimeMillis() : limit.getCooldown();
  }

  public void checkDailySellReset(String cooldownStr) {
    if (dailySellEarnings == null) {
      dailySellEarnings = new HashMap<>();
    }
    if (System.currentTimeMillis() >= dailySellReset) {
      dailySellEarnings.clear();
      long expiration = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
      try {
        expiration = parseCooldown(cooldownStr).toNextEpochMillis(Instant.now());
      } catch (Exception ignored) {
      }
      dailySellReset = expiration;
    }
  }

  public BigDecimal getDailySellEarnings(String currency) {
    if (dailySellEarnings == null) {
      dailySellEarnings = new HashMap<>();
    }
    return dailySellEarnings.getOrDefault(currency, BigDecimal.ZERO);
  }

  public void addDailySellEarnings(String currency, BigDecimal amount, String cooldownStr) {
    checkDailySellReset(cooldownStr);
    BigDecimal current = dailySellEarnings.getOrDefault(currency, BigDecimal.ZERO);
    dailySellEarnings.put(currency, current.add(amount));
  }

  /**
   * Returns the current sell count for a product.
   */
  public int getActualProductSellLimit(Product product) {
    if (product.getSellUuid() == null || product.getSellMax() == null) return 0;
    if (cooldownProductSell == null) cooldownProductSell = new HashMap<>();
    ProductLimit limit = cooldownProductSell.get(product.getSellUuid());
    if (limit == null) return 0;
    if (limit.getCooldown() <= System.currentTimeMillis()) {
      cooldownProductSell.remove(product.getSellUuid());
      return 0;
    }
    return limit.getAmount();
  }

  /**
   * Adds a sale to the product sell limit tracker.
   */
  public void addDailyProductSellLimit(Product product, int amount) {
    if (product.getSellUuid() == null || product.getSellMax() == null || product.getSellCooldown() == null) return;
    if (cooldownProductSell == null) cooldownProductSell = new HashMap<>();
    ProductLimit limit = cooldownProductSell.computeIfAbsent(product.getSellUuid(), k -> {
      ProductLimit pl = new ProductLimit();
      pl.setUuid(product.getSellUuid());
      long expiration = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60);
      try {
        expiration = parseCooldown(product.getSellCooldown()).toNextEpochMillis(Instant.now());
      } catch (Exception ignored) {
      }
      pl.setCooldown(expiration);
      pl.setAmount(0);
      return pl;
    });
    limit.setAmount(limit.getAmount() + amount);
  }

  /**
   * Whether the player can sell a limited product.
   */
  public boolean canSellProduct(Product product) {
    if (product.getSellUuid() == null || product.getSellMax() == null) return true;
    if (cooldownProductSell == null) cooldownProductSell = new HashMap<>();

    ProductLimit limit = cooldownProductSell.get(product.getSellUuid());
    if (limit == null) return true;

    boolean onCooldown = limit.getCooldown() > System.currentTimeMillis();
    if (!onCooldown) {
      cooldownProductSell.remove(product.getSellUuid());
      return true;
    }

    return limit.getAmount() < product.getSellMax();
  }

  /**
   * Returns the sell cooldown expiration time for a product.
   */
  public long getProductSellCooldown(Product product) {
    if (product.getSellUuid() == null) return System.currentTimeMillis();
    if (cooldownProductSell == null) cooldownProductSell = new HashMap<>();
    ProductLimit limit = cooldownProductSell.get(product.getSellUuid());
    return limit == null ? System.currentTimeMillis() : limit.getCooldown();
  }

  // --- Shop Sell Limits Helpers ---

  public void checkShopDailySellReset(String shopId, String cooldownStr) {
    if (shopDailySellEarnings == null) shopDailySellEarnings = new HashMap<>();
    if (shopDailySellReset == null) shopDailySellReset = new HashMap<>();

    long resetTime = shopDailySellReset.getOrDefault(shopId, 0L);
    if (System.currentTimeMillis() >= resetTime) {
      shopDailySellEarnings.remove(shopId);
      long expiration = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
      try {
        expiration = parseCooldown(cooldownStr).toNextEpochMillis(Instant.now());
      } catch (Exception ignored) {
      }
      shopDailySellReset.put(shopId, expiration);
    }
  }

  public BigDecimal getShopDailySellEarnings(String shopId, String currency) {
    if (shopDailySellEarnings == null) shopDailySellEarnings = new HashMap<>();
    Map<String, BigDecimal> shopEarnings = shopDailySellEarnings.get(shopId);
    if (shopEarnings == null) return BigDecimal.ZERO;
    return shopEarnings.getOrDefault(currency, BigDecimal.ZERO);
  }

  public void addShopDailySellEarnings(String shopId, String currency, BigDecimal amount, String cooldownStr) {
    checkShopDailySellReset(shopId, cooldownStr);
    Map<String, BigDecimal> shopEarnings = shopDailySellEarnings.computeIfAbsent(shopId, k -> new HashMap<>());
    BigDecimal current = shopEarnings.getOrDefault(currency, BigDecimal.ZERO);
    shopEarnings.put(currency, current.add(amount));
  }

  public DynamicRotation getOrCreateRotation(String shopId) {
    if (rotationShops == null) {
      rotationShops = new HashMap<>();
    }
    return rotationShops.computeIfAbsent(shopId, k -> new DynamicRotation());
  }

  /**
   * Removes expired product limits that no longer exist in any shop.
   */
  public boolean cleanupOrphanedLimits(Set<UUID> validProductUuids) {
    return cooldownProduct.keySet().removeIf(id -> !validProductUuids.contains(id));
  }
}

