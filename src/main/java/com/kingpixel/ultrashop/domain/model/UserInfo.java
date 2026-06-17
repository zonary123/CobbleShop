package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.ScheduleValue;
import com.kingpixel.cobbleutils.Model.DurationValue;
import java.time.Instant;
import java.time.ZoneId;
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

  /**
   * Removes expired product limits that no longer exist in any shop.
   */
  public boolean cleanupOrphanedLimits(Set<UUID> validProductUuids) {
    return cooldownProduct.keySet().removeIf(id -> !validProductUuids.contains(id));
  }
}

