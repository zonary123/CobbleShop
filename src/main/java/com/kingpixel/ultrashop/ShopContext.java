package com.kingpixel.ultrashop;

import com.kingpixel.cobbleutils.util.async.AsyncContext;
import com.kingpixel.cobbleutils.util.async.UtilsAsync;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.infrastructure.index.SellProductIndex;
import com.kingpixel.ultrashop.infrastructure.persistence.RepositoryFactory;
import com.kingpixel.ultrashop.infrastructure.web.DashboardHttpServer;
import lombok.Data;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central context for UltraShop — replaces all static mutable state.
 * Single source of truth for server, async, config, lang, shops, repositories.
 */
@Data
public final class ShopContext {

  private static final ShopContext INSTANCE = new ShopContext();

  public AsyncContext getAsyncContext() {
    return UtilsAsync.createContext(UltraShop.MOD_ID, "UltraShop");
  }

  @Getter
  private final Map<String, ShopConfig> configs = new ConcurrentHashMap<>();

  @Getter
  private final Map<String, List<com.kingpixel.ultrashop.domain.model.Shop>> shops = new ConcurrentHashMap<>();

  @Getter
  private volatile LangConfig lang;

  @Getter
  private volatile com.kingpixel.ultrashop.domain.model.DataShop dataShop;

  @Getter
  private volatile SellProductIndex sellIndex;

  @Getter
  @lombok.Setter
  private volatile RepositoryFactory repositories;

  private final ConcurrentHashMap<java.util.UUID, Object> transactionLocks = new ConcurrentHashMap<>();

  private volatile DashboardHttpServer dashboardServer;

  public Object getTransactionLock(java.util.UUID uuid) {
    return transactionLocks.computeIfAbsent(uuid, k -> new Object());
  }

  private ShopContext() {
  }

  public static ShopContext get() {
    return INSTANCE;
  }

  /**
   * Initialize the async context. Called once during mod init.
   */
  public void init() {
    this.dataShop = new com.kingpixel.ultrashop.domain.model.DataShop();
    this.sellIndex = new SellProductIndex();
    this.lang = new LangConfig();
  }

  
  /**
   * Run a task on the server main thread. Safe for inventory modifications.
   */
  public void runOnServer(Runnable task) {
    if (com.kingpixel.cobbleutils.CobbleUtils.server != null) {
      com.kingpixel.cobbleutils.CobbleUtils.server.execute(task);
    }
  }

  /**
   * Graceful shutdown of async context.
   */
  public void shutdown() {
    stopDashboard();
    if (repositories != null) {
      repositories.close();
    }
  }

  /**
   * Start the web dashboard if enabled in the main config.
   */
  public void startDashboard() {
    ShopConfig config = getMainConfig();
    if (config != null && config.isWebDashboardEnabled()) {
      stopDashboard();
      dashboardServer = new DashboardHttpServer(config.getWebDashboardPort(), config.getWebDashboardPassword());
      dashboardServer.start();
    }
  }

  /**
   * Stop the web dashboard if running.
   */
  public void stopDashboard() {
    if (dashboardServer != null) {
      dashboardServer.stop();
      dashboardServer = null;
    }
  }

  /**
   * Get the main config (for the ultrashop mod itself).
   */
  public ShopConfig getMainConfig() {
    return configs.get(UltraShop.MOD_ID);
  }

  /**
   * Get shops for a specific mod.
   */
  public List<com.kingpixel.ultrashop.domain.model.Shop> getShops(String modId) {
    return shops.getOrDefault(modId, List.of());
  }
}

