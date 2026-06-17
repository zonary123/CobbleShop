package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Sealed root of the Shop type hierarchy. Replaces the god-object
 * {@code com.kingpixel.ultrashop.domain.model.Shop} with explicit polymorphism.
 *
 * <p>Each {@link ShopType} maps to exactly one implementation:</p>
 * <ul>
 *   <li>{@link ShopType#NORMAL} → {@link NormalShop} — static product catalog</li>
 *   <li>{@link ShopType#CATEGORY} → {@link CategoryShop} — menu of sub-shops</li>
 *   <li>{@link ShopType#ROTATION} → {@link RotationShop} — dynamic catalog driven by Scheduler</li>
 * </ul>
 *
 * <p>Use {@link #accept(ShopVisitor)} to dispatch behavior polymorphically — the
 * compiler will refuse to compile any visitor that doesn't handle every subtype.</p>
 *
 * <p><b>Migration note:</b> This interface coexists with the legacy
 * {@code com.kingpixel.ultrashop.domain.model.Shop} class during the multi-phase
 * refactor. Use {@link ShopBridge} to convert between the two representations.
 * Once all call sites migrate, the legacy class is deleted (Phase 4 cleanup).</p>
 */
public sealed interface Shop extends ShopReference
  permits NormalShop, CategoryShop, RotationShop {

  String getId();

  ShopType getType();

  DisplayConfig getDisplayConfig();
  void setDisplayConfig(DisplayConfig displayConfig);

  EconomyConfig getEconomyConfig();
  void setEconomyConfig(EconomyConfig economyConfig);

  ConditionsConfig getConditionsConfig();
  void setConditionsConfig(ConditionsConfig conditionsConfig);

  SoundConfig getSoundConfig();
  void setSoundConfig(SoundConfig soundConfig);

  boolean isMaintenance();

  void setMaintenance(boolean maintenance);

  String getWebhookUrl();
  void setWebhookUrl(String webhookUrl);

  String getFilePath();
  void setFilePath(String filePath);

  /** Validates and fills in defaults. Idempotent — safe to call multiple times. */
  void check();

  /**
   * Products visible in this shop right now. Behavior is type-specific:
   * <ul>
   *   <li>{@link NormalShop}: full static catalog</li>
   *   <li>{@link CategoryShop}: empty (use {@code getSubShops()} instead)</li>
   *   <li>{@link RotationShop}: current rotation contents</li>
   * </ul>
   */
  List<Product> activeProducts();

  /**
   * Polymorphic dispatch entry point. Implementations call
   * {@code visitor.visit(this)} on their concrete type.
   */
  <R> R accept(ShopVisitor<R> visitor);

  /**
   * Convenience: first economy in {@link EconomyConfig#getEconomies()}, or {@code null}.
   */
  default EconomyUse getPrimaryEconomy() {
    EconomyConfig eco = getEconomyConfig();
    return eco == null || eco.getEconomies() == null
      ? null
      : eco.getEconomies().stream().findFirst().orElse(null);
  }

  /**
   * Permission node for this shop, namespaced by the owning mod id.
   */
  default String getPermission(String modId) {
    String prefix = UltraShop.MOD_ID.equals(modId) ? UltraShop.MOD_ID : modId + ".shop";
    return prefix + ".shops." + getId();
  }

  // --- ShopReference adapter methods ---
  // Delegate to the underlying VOs so callers using the legacy fluent API
  // (Product, PriceCalculator, ...) work transparently with both representations.

  @Override
  default LinkedHashSet<EconomyUse> getEconomies() {
    EconomyConfig eco = getEconomyConfig();
    return eco != null && eco.getEconomies() != null
      ? eco.getEconomies()
      : new LinkedHashSet<>();
  }

  @Override
  default boolean isAutoPlace() {
    DisplayConfig display = getDisplayConfig();
    return display != null && display.isAutoPlace();
  }

  @Override
  default float getGlobalDiscount() {
    EconomyConfig eco = getEconomyConfig();
    return eco != null ? eco.getGlobalDiscount() : 0f;
  }

  @Override
  default java.util.Map<String, Float> getDiscounts() {
    EconomyConfig eco = getEconomyConfig();
    return eco != null && eco.getDiscounts() != null
      ? eco.getDiscounts()
      : java.util.Map.of();
  }
}





