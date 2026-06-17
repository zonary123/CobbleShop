package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.shop.ShopReference;
import lombok.Data;
import net.minecraft.item.ItemStack;

import java.math.BigDecimal;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A product that can be bought/sold in a shop.
 * Pure domain model — no GUI, no I/O, no serialization logic.
 *
 * <p>Only {@code product}, {@code buy}, and {@code sell} are required.
 * All other fields are nullable — omit them in JSON if not needed.
 * The product self-corrects at runtime via {@code check()} without polluting the JSON.</p>
 */
@Data
public class Product {

  // --- Core (required) ---
  private String product;
  private BigDecimal buy;
  private BigDecimal sell;

  // --- Multi-currency pricing (optional — when set, buy/sell are ignored) ---
  @Nullable private List<PriceEntry> prices;

  // --- Display overrides (all optional) ---
  @Nullable private String display;
  @Nullable private String displayname;
  @Nullable private List<String> lore;
  @Nullable private Integer CustomModelData;

  // --- Layout (optional) ---
  @Nullable private Integer slot;

  // --- Discount (optional) ---
  @Nullable private Float discount;

  // --- Limit/cooldown (optional — set max or cooldown to enable limits) ---
  @Nullable private Boolean oneByOne;
  @Nullable private UUID uuid;
  @Nullable private Integer max;
  @com.google.gson.annotations.JsonAdapter(com.kingpixel.ultrashop.infrastructure.serialization.CooldownTypeAdapter.class)
  @Nullable private String cooldown;

  // --- Stock (optional) ---
  @Nullable private StockMode stockMode;
  @Nullable private Integer stockAmount;

  // --- Dynamic Rotation weight (optional — only relevant in rotational shops) ---
  @Nullable private Integer chance;

  // --- Conditions (optional — only add when needed) ---
  @Nullable private List<Condition> conditions;
  @Nullable private List<Condition> visibilityConditions;

  // --- Legacy permission fields (kept for migration only, use conditions instead) ---
  @Nullable private String canBuyPermission;
  @Nullable private String notBuyPermission;

  public Product() {
    this.product = "minecraft:stone";
    this.buy = BigDecimal.valueOf(9999999);
    this.sell = BigDecimal.ZERO;
  }

  /**
   * Creates a product with all optional fields populated (for default config example).
   */
  public Product(boolean withOptionalFields) {
    this();
    if (withOptionalFields) {
      this.displayname = "Custom Stone";
      this.display = "minecraft:stone";
      this.lore = List.of("This is a custom stone", "You can use it to build");
      this.CustomModelData = 0;
      this.slot = 0;
      this.discount = 10.0f;
      this.oneByOne = true;
      this.uuid = UUID.randomUUID();
      this.max = 1;
      this.cooldown = "60m";
      this.chance = 100;
      this.conditions = new ArrayList<>();
      this.visibilityConditions = new ArrayList<>();
    }
  }

  /**
   * Returns the effective price entries for this product, deduplicated by economy.
   *
   * <ul>
   *   <li><b>Multi-currency mode</b>: {@code prices} list is set — deduplicated by economy key,
   *       last entry wins if the same economy appears twice in the JSON.</li>
   *   <li><b>Simple mode</b>: only {@code buy}/{@code sell} are set — uses the shop's primary
   *       economy (first unique entry). Duplicate economies in the shop are ignored, preventing
   *       the x2 charge bug.</li>
   * </ul>
   */
  public List<PriceEntry> getEffectivePrices(@NotNull ShopReference shop) {
    if (prices != null && !prices.isEmpty()) {
      // Deduplicate by economy — LinkedHashMap preserves insertion order
      LinkedHashMap<EconomyUse, PriceEntry> deduped = new LinkedHashMap<>();
      for (PriceEntry entry : prices) {
        deduped.put(entry.getEconomy(), entry);
      }
      return new ArrayList<>(deduped.values());
    }

    // Simple mode: one PriceEntry per unique economy in the shop (LinkedHashSet garantiza unicidad)
    LinkedHashSet<EconomyUse> uniqueEconomies = shop.getEconomies();    List<PriceEntry> result = new ArrayList<>(uniqueEconomies.size());
    for (EconomyUse eco : uniqueEconomies) {
      result.add(new PriceEntry(eco, buy, sell));
    }
    return result;
  }

  /**
   * Whether this product can be bought (in any economy).
   */
  public boolean isBuyable() {
    if (prices != null && !prices.isEmpty()) {
      return prices.stream().anyMatch(PriceEntry::isBuyable);
    }
    return buy != null && buy.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Whether this product can be sold (in any economy).
   */
  public boolean isSellable() {
    if (prices != null && !prices.isEmpty()) {
      return prices.stream().anyMatch(PriceEntry::isSellable);
    }
    return sell != null && sell.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Validates required fields only. Does NOT assign defaults to optional fields
   * so that the JSON stays clean — only user-set fields appear in the file.
   */
  public void check(@NotNull ShopReference shop) {
    if (product == null) product = "minecraft:stone";
    if (!shop.isAutoPlace() && slot == null) slot = 0;

    // Auto-generate UUID for products with limits
    if (cooldown != null || max != null) {
      if (uuid == null) uuid = UUID.randomUUID();
      if (max == null) max = 1;
      if (cooldown == null) cooldown = "60m";
    }

    if (stockAmount != null) {
      if (stockAmount <= 0) {
        stockAmount = null;
        stockMode = null;
      } else {
        if (uuid == null) uuid = UUID.randomUUID();
        if (stockMode == null) stockMode = StockMode.PLAYER;
      }
    }
  }

  public boolean hasStockControl() {
    return stockAmount != null && stockAmount > 0 && stockMode != null && uuid != null;
  }

  /**
   * Whether the product has a pricing error (sell > buy in any entry).
   */
  public boolean hasErrors() {
    if (prices != null && !prices.isEmpty()) {
      for (PriceEntry entry : prices) {
        if (entry.isBuyable() && entry.isSellable()
          && entry.getBuy().compareTo(entry.getSell()) < 0) {
          UltraShop.LOGGER.error("Sell price higher than buy price in multi-currency product -> " + product);
          return true;
        }
      }
      return false;
    }
    if (isBuyable() && isSellable() && buy.compareTo(sell) < 0) {
      UltraShop.LOGGER.error("The sell price is higher than the buy price -> " + product);
      return true;
    }
    return false;
  }

  /**
   * Returns the max stack size for this product type.
   */
  public int getMaxStack() {
    if (product.startsWith("command:") || product.startsWith("pokemon:") || Boolean.TRUE.equals(oneByOne)) {
      return 1;
    }
    return new ItemChance(product, 0).getItemStack().getMaxCount();
  }

  /**
   * Creates an ItemStack from this product's identifier.
   */
  public ItemStack getItemStack() {
    return new ItemChance(product, 0).getItemStack();
  }

  /**
   * Whether this product can be sold (excludes commands, pokemon, multi-items).
   */
  public boolean canBeSold() {
    if (!isSellable()) return false;
    return !product.startsWith("command:") && !product.startsWith("pokemon:") && !product.contains("|");
  }

  /**
   * Returns the effective chance weight for rotation selection.
   */
  public int getEffectiveChance() {
    return chance != null ? chance : 100;
  }
}
