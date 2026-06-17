package com.kingpixel.ultrashop.domain.model;

/**
 * Stock mode for products with limited availability.
 * PLAYER: each player has its own independent stock pool.
 * GLOBAL: all players share the same stock pool.
 */
public enum StockMode {
  PLAYER,
  GLOBAL
}

