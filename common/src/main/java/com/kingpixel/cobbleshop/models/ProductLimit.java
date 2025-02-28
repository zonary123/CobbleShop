package com.kingpixel.cobbleshop.models;

import lombok.Data;

import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:32
 */
@Data
public class ProductLimit {
  private UUID uuid;
  private int amount;
  private long cooldown;
}
