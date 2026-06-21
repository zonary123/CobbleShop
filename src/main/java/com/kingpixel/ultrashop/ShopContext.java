package com.kingpixel.ultrashop;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.async.AsyncContext;
import com.kingpixel.cobbleutils.util.async.UtilsAsync;
import com.kingpixel.ultrashop.domain.model.DataShop;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopBridge;
import com.kingpixel.ultrashop.domain.model.shop.ShopVisitor;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.infrastructure.index.SellProductIndex;
import com.kingpixel.ultrashop.infrastructure.persistence.RepositoryFactory;
import com.kingpixel.ultrashop.infrastructure.web.DashboardHttpServer;
import lombok.Data;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Central context for UltraShop — replaces all static mutable state.
 * Single source of truth for server, async, config, lang, shops, repositories.
 */
@Data
public final class ShopContext {

  private static final ShopContext INSTANCE = new ShopContext();

  private final AsyncContext asyncContext = UtilsAsync.createContext(UltraShop.MOD_ID, "UltraShop");

  @Getter
  private final Map<String, ShopConfig> configs = new ConcurrentHashMap<>();

  @Getter
  private final Map<String, List<Shop>> shops = new ConcurrentHashMap<>();

  /**
   * Parallel storage of shops in the new sealed {@link com.kingpixel.ultrashop.domain.model.shop.Shop}
   * hierarchy. Populated by {@code ConfigLoader.loadShops} alongside the legacy
   * {@link #shops} map via {@link com.kingpixel.ultrashop.domain.model.shop.ShopBridge}.
   *
   * <p>New consumers (visitor-based GUI builders, future cross-server services)
   * read from this map. Legacy consumers keep using {@link #shops}. Both maps
   * stay in sync until every callsite migrates and the legacy map is removed.</p>
   */
  @Getter
  private final Map<String, List<com.kingpixel.ultrashop.domain.model.shop.Shop>> typedShops = new ConcurrentHashMap<>();

  @Getter
  private volatile LangConfig lang;

  @Getter
  private volatile DataShop dataShop;

  @Getter
  private volatile SellProductIndex sellIndex;

  @Getter
  @lombok.Setter
  private volatile RepositoryFactory repositories;

  private final ConcurrentHashMap<UUID, Object> transactionLocks = new ConcurrentHashMap<>();

  private volatile DashboardHttpServer dashboardServer;

  public Object getTransactionLock(UUID uuid) {
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
    this.dataShop = new DataShop();
    this.sellIndex = new SellProductIndex();
    this.lang = new LangConfig();
  }


  /**
   * Run a task on the server main thread. Safe for inventory modifications.
   */
  public void runOnServer(Runnable task) {
    if (CobbleUtils.server != null) {
      CobbleUtils.server.execute(task);
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
      if (config.getWebDashboardPassword() == null || config.getWebDashboardPassword().isBlank()) {
        UltraShop.LOGGER.error(
          "[Web] Dashboard enabled but no password configured. Refusing to start insecure dashboard.");
        return;
      }
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

  /**
   * Get shops for a specific mod in the new sealed hierarchy.
   * Use this when consuming shops via {@link ShopVisitor}.
   */
  public List<com.kingpixel.ultrashop.domain.model.shop.Shop> getTypedShops(String modId) {
    return typedShops.getOrDefault(modId, Collections.emptyList());
  }

  // --- Typed shop mutators ------------------------------------------------
  // Keep both maps (legacy + typed) in lock-step. When a caller mutates the
  // typed view we also rewrite the legacy mirror via ShopBridge so the legacy
  // call-sites that haven't migrated yet still see the change. Removed in
  // Lote 4 once the legacy map is gone.

  /**
   * Replace an existing typed shop in-place (matched by id). Mirrors the
   * change into the legacy map so unmigrated callers stay consistent.
   *
   * @return {@code true} if a shop with that id existed and was replaced
   */
  public boolean replaceShop(String modId, com.kingpixel.ultrashop.domain.model.shop.Shop shop) {
    List<com.kingpixel.ultrashop.domain.model.shop.Shop> typedList =
      typedShops.computeIfAbsent(modId,
        k -> new CopyOnWriteArrayList<com.kingpixel.ultrashop.domain.model.shop.Shop>());
    boolean replacedTyped = replaceById(typedList, shop, com.kingpixel.ultrashop.domain.model.shop.Shop::getId);

    var legacyList = shops.computeIfAbsent(modId, k -> new CopyOnWriteArrayList<>());
    var legacyShop = ShopBridge.toLegacy(shop);
    replaceById(legacyList, legacyShop, com.kingpixel.ultrashop.domain.model.Shop::getId);

    return replacedTyped;
  }

  /**
   * Append a new typed shop. Also bridges into the legacy map so the legacy
   * call-sites still observe it.
   */
  public void addTypedShop(String modId, com.kingpixel.ultrashop.domain.model.shop.Shop shop) {
    typedShops.computeIfAbsent(modId,
      k -> new CopyOnWriteArrayList<com.kingpixel.ultrashop.domain.model.shop.Shop>()).add(shop);
    shops.computeIfAbsent(modId, k -> new CopyOnWriteArrayList<>())
      .add(ShopBridge.toLegacy(shop));
  }

  /**
   * Remove a typed shop by id. Mirrors the removal into the legacy map.
   *
   * @return {@code true} if a shop with that id was found and removed
   */
  public boolean removeTypedShop(String modId, String shopId) {
    boolean removed = false;
    var typedList = typedShops.get(modId);
    if (typedList != null) {
      removed = typedList.removeIf(s -> shopId.equals(s.getId()));
    }
    var legacyList = shops.get(modId);
    if (legacyList != null) {
      legacyList.removeIf(s -> shopId.equals(s.getId()));
    }
    return removed;
  }

  private static <T> boolean replaceById(List<T> list, T incoming, Function<T, String> idFn) {
    String id = idFn.apply(incoming);
    for (int i = 0; i < list.size(); i++) {
      if (id.equals(idFn.apply(list.get(i)))) {
        list.set(i, incoming);
        return true;
      }
    }
    list.add(incoming);
    return false;
  }
}

