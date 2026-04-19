package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

import java.util.UUID;

/**
 * Tracks buy limits and cooldowns for a specific product per user.
 */
@Data
public class ProductLimit {
  private UUID uuid;
  private int amount;
  private long cooldown;
}

