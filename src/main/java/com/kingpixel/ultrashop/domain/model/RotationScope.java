package com.kingpixel.ultrashop.domain.model;

/**
 * Defines whether a {@link com.kingpixel.ultrashop.domain.model.shop.RotationShop}
 * shares one rotation across the server or keeps a separate catalog per player.
 */
public enum RotationScope {
  /** One rotation state for the whole server (default). */
  GLOBAL,
  /** Each player has their own rotation timer and product selection. */
  PLAYER,
  /** Shared rotation catalog per guild. */
  GUILD
}
