package com.kingpixel.ultrashop.infrastructure.persistence.json;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.StockMode;
import com.kingpixel.ultrashop.infrastructure.persistence.StockRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;

/**
 * Local JSON stock repository.
 * Uses a process-local lock to keep read-modify-write operations atomic.
 */
public class JsonStockRepository implements StockRepository {

  private final Path filePath;
  private final Object lock = new Object();
  private StockData data;

  public JsonStockRepository() {
    this.filePath = CobbleUtils.getPath().resolve(UltraShop.MOD_ID).resolve("data").resolve("stock.json");
  }

  @Override
  public long getRemaining(UUID playerUuid, UUID productUuid, StockMode mode, long maxStock) {
    synchronized (lock) {
      ensureLoaded();
      long consumed = data.consumed.getOrDefault(key(playerUuid, productUuid, mode), 0L);
      return Math.max(0L, maxStock - consumed);
    }
  }

  @Override
  public boolean tryConsume(UUID playerUuid, UUID productUuid, StockMode mode, int amount, long maxStock) {
    if (amount <= 0 || maxStock <= 0) return false;
    synchronized (lock) {
      ensureLoaded();
      String key = key(playerUuid, productUuid, mode);
      long consumed = data.consumed.getOrDefault(key, 0L);
      long next = consumed + amount;
      if (next > maxStock) {
        return false;
      }
      data.consumed.put(key, next);
      persist();
      return true;
    }
  }

  @Override
  public void release(UUID playerUuid, UUID productUuid, StockMode mode, int amount) {
    if (amount <= 0) return;
    synchronized (lock) {
      ensureLoaded();
      String key = key(playerUuid, productUuid, mode);
      long consumed = data.consumed.getOrDefault(key, 0L);
      long next = Math.max(0L, consumed - amount);
      if (next == 0L) {
        data.consumed.remove(key);
      } else {
        data.consumed.put(key, next);
      }
      persist();
    }
  }

  private void ensureLoaded() {
    if (data != null) return;
    try {
      if (UtilsFile.exists(filePath)) {
        StockData loaded = UtilsFile.read(filePath, StockData.class);
        data = loaded != null ? loaded : new StockData();
      } else {
        data = new StockData();
      }
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error loading stock data from " + filePath, e);
      data = new StockData();
    }
  }

  private void persist() {
    try {
      UtilsFile.write(filePath, data);
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error saving stock data to " + filePath, e);
    }
  }

  private static String key(UUID playerUuid, UUID productUuid, StockMode mode) {
    if (mode == StockMode.GLOBAL) {
      return "g:" + productUuid;
    }
    return "p:" + playerUuid + ":" + productUuid;
  }

  private static final class StockData {
    private Map<String, Long> consumed = new HashMap<>();
  }
}

