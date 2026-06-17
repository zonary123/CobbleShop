package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.ultrashop.domain.model.StockMode;

import java.util.UUID;

/**
 * Repository for product stock counters.
 * Supports PLAYER and GLOBAL stock modes.
 */
public interface StockRepository {
  long getRemaining(UUID playerUuid, UUID productUuid, StockMode mode, long maxStock);

  boolean tryConsume(UUID playerUuid, UUID productUuid, StockMode mode, int amount, long maxStock);

  void release(UUID playerUuid, UUID productUuid, StockMode mode, int amount);
}

