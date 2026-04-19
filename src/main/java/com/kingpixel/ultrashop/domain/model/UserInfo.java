package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

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

  public UserInfo() {
  }

  public UserInfo(UUID uuid, String name) {
    this.uuid = uuid;
    this.name = name;
  }

  /**
   * Returns the current buy count for a product.
   */
  public int getActualProductLimit(Product product) {
    ProductLimit limit = cooldownProduct.get(product.getUuid());
    return limit == null ? 0 : limit.getAmount();
  }

  /**
   * Adds a purchase to the product limit tracker.
   */
  public void addProductLimit(Product product, int amount) {
    ProductLimit limit = cooldownProduct.computeIfAbsent(product.getUuid(), k -> {
      ProductLimit pl = new ProductLimit();
      pl.setUuid(product.getUuid());
      pl.setCooldown(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(product.getCooldown()));
      pl.setAmount(0);
      return pl;
    });
    limit.setAmount(limit.getAmount() + amount);
  }

  /**
   * Whether the player can buy a limited product.
   */
  public boolean canBuy(Product product) {
    if (product.getUuid() == null) return true;

    ProductLimit limit = cooldownProduct.get(product.getUuid());
    if (limit == null) return true;

    boolean atLimit = limit.getAmount() >= product.getMax();
    boolean onCooldown = limit.getCooldown() > System.currentTimeMillis();

    if (atLimit && !onCooldown) {
      // Cooldown expired — reset limit
      cooldownProduct.remove(product.getUuid());
      return true;
    }

    return !atLimit;
  }

  /**
   * Returns the cooldown expiration time for a product.
   */
  public long getProductCooldown(Product product) {
    ProductLimit limit = cooldownProduct.get(product.getUuid());
    return limit == null ? System.currentTimeMillis() : limit.getCooldown();
  }

  /**
   * Removes expired product limits that no longer exist in any shop.
   */
  public boolean cleanupOrphanedLimits(Set<UUID> validProductUuids) {
    boolean changed = cooldownProduct.keySet().removeIf(uuid -> !validProductUuids.contains(uuid));
    return changed;
  }
}

