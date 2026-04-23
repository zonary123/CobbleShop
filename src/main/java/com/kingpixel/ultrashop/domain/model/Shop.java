package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.*;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.util.economys.providers.ImpactorEconomy;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A shop definition — pure domain model.
 * No GUI logic, no I/O, no serialization.
 *
 * <p>Behavior is selected by {@link #type}:</p>
 * <ul>
 *   <li>{@link ShopType#NORMAL} — static catalog of {@code products}</li>
 *   <li>{@link ShopType#CATEGORY} — menu of {@code subShops}</li>
 *   <li>{@link ShopType#ROTATION} — dynamic catalog driven by {@link RotationSchedule}</li>
 * </ul>
 *
 * <p>{@link #openConditions} controls accessibility orthogonally to {@code type}
 * (replaces the old Weekly/Calendar shop subclasses).</p>
 */
@Data
public class Shop implements com.kingpixel.ultrashop.domain.model.shop.ShopReference {


  // --- Transient (not serialized) ---
  private transient String filePath;
  private transient String id;

  // --- Type ---
  @NotNull
  private ShopType type = ShopType.NORMAL;

  // --- Display & Layout ---
  private String name = "Shop";
  private String title;
  private boolean autoPlace;
  private int rows;
  private String colorProduct;
  private Rectangle rectangle;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private ItemModel itemBalance;
  private ItemModel itemPrevious;
  private ItemModel itemClose;
  private ItemModel itemNext;
  private List<PanelsConfig> panels;

  // --- Sound ---
  private String soundOpen;
  private String soundClose;

  // --- Economy (Multi-Currency) ---
  @NotNull
  private LinkedHashSet<EconomyUse> economies;

  private float globalDiscount;
  @NotNull
  private Map<String, Float> discounts;

  // --- Behavior ---
  @Nullable
  private String closeCommand;
  private boolean announceRotation;

  // --- Dynamic Rotation Schedule ---
  @Nullable
  private RotationSchedule rotationSchedule;

  // --- Conditions ---
  @NotNull
  private List<Condition> openConditions;

  // --- Content ---
  private List<SubShop> subShops;
  private List<Product> products;


  public Shop() {
    this.autoPlace = true;
    this.id = "shop";
    this.name = "Shop";
    this.title = "%shop%";
    this.closeCommand = "";
    this.colorProduct = "";
    this.soundOpen = "minecraft:block.chest.open";
    this.soundClose = "minecraft:block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.openConditions = new ArrayList<>();
    this.discounts = new HashMap<>();
    this.discounts.put("group.vip", 2.0f);
    this.rectangle = new Rectangle(1, 1, 4, 7);
    this.economies = new LinkedHashSet<>(List.of(new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars")));
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    this.itemInfoShop.setSlot(51);
    this.itemBalance = new ItemModel("");
    this.itemBalance.setSlot(47);
    this.products = defaultProducts();
    this.itemPrevious = new ItemModel("");
    this.itemPrevious.setSlot(45);
    this.itemClose = new ItemModel("");
    this.itemClose.setSlot(49);
    this.itemNext = new ItemModel("");
    this.itemNext.setSlot(53);
    this.subShops = new ArrayList<>();
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  /**
   * Creates a shop with a specific id and explicit type.
   */
  public Shop(String id, ShopType type) {
    this();
    this.id = id;
    this.type = type != null ? type : ShopType.NORMAL;
    if (this.type == ShopType.ROTATION) {
      this.rotationSchedule = new RotationSchedule("30m", 3);
    }
  }

  /**
   * @deprecated Use {@link #Shop(String, ShopType)} instead.
   */
  @Deprecated
  public Shop(String id, boolean hasRotation) {
    this(id, hasRotation ? ShopType.ROTATION : ShopType.NORMAL);
  }

  /**
   * Returns the primary (first unique) economy, or {@code null} if none is configured.
   */
  public EconomyUse getPrimaryEconomy() {
    return economies.stream().findFirst().orElse(null);
  }

  /**
   * Validates and fills in defaults for missing fields.
   * Also auto-promotes legacy shops (without explicit {@code type}) based on
   * which fields are populated.
   */
  public void check() {
    if (subShops == null) subShops = new ArrayList<>();
    if (economies.isEmpty()) {
      economies = new LinkedHashSet<>();
      economies.add(new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars"));
    }
    if (openConditions == null) openConditions = new ArrayList<>();
    if (discounts == null) discounts = new HashMap<>();
    if (products == null) products = new ArrayList<>();
    if (panels == null) panels = List.of(new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows));

    // Auto-promote legacy configs (no explicit type)
    if (type == null) type = ShopType.NORMAL;
    if (type == ShopType.NORMAL) {
      if (rotationSchedule != null) {
        type = ShopType.ROTATION;
      } else if (!subShops.isEmpty()) {
        type = ShopType.CATEGORY;
      }
    }

    // Sanity: rotation requires a schedule
    if (type == ShopType.ROTATION && rotationSchedule == null) {
      UltraShop.LOGGER.warn("Shop '" + id + "' is ROTATION but has no rotationSchedule. Defaulting to 1h interval.");
      rotationSchedule = new RotationSchedule("1h", 3);
    }

    products.forEach(product -> product.check(this));
    validateUniqueProductUuids();
  }

  /**
   * Returns the permission node for this shop.
   */
  public String getPermission(String modId) {
    String prefix = modId.equals(UltraShop.MOD_ID) ? UltraShop.MOD_ID : modId + ".shop";
    return prefix + ".shops." + id;
  }

  /**
   * Whether this shop has sub-shops (categories) instead of direct products.
   * @deprecated Use {@code getType() == ShopType.CATEGORY} instead.
   */
  @Deprecated
  public boolean hasCategories() {
    return type == ShopType.CATEGORY;
  }

  /** Convenience: shop is a static product catalog. */
  public boolean isNormal() { return type == ShopType.NORMAL; }

  /** Convenience: shop is a category menu (uses {@code subShops}). */
  public boolean isCategory() { return type == ShopType.CATEGORY; }

  /** Convenience: shop has rotating dynamic products. */
  public boolean isRotation() { return type == ShopType.ROTATION; }

  // --- Phase 1 refactor: Value Object snapshots ---
  // These accessors return immutable views composed from the legacy flat fields.
  // The Shop class remains the canonical data store; the on-disk JSON shape is
  // unchanged. Downstream code (Phase 3 adapters, GUI builders) can start
  // consuming these VOs without breaking existing configs.

  /**
   * Returns an immutable snapshot of the visual / layout configuration.
   * Built read-through from the legacy {@code Shop} fields — mutations to the
   * Shop after this call are NOT reflected in the returned snapshot.
   */
  public DisplayConfig toDisplayConfig() {
    return DisplayConfig.builder()
      .name(name)
      .title(title)
      .autoPlace(autoPlace)
      .rows(rows)
      .colorProduct(colorProduct)
      .rectangle(rectangle)
      .displayItem(display)
      .itemInfoShop(itemInfoShop)
      .itemBalance(itemBalance)
      .itemPrevious(itemPrevious)
      .itemClose(itemClose)
      .itemNext(itemNext)
      .panels(panels)
      .build();
  }

  /**
   * Returns an immutable snapshot of the economy configuration.
   */
  public EconomyConfig toEconomyConfig() {
    return EconomyConfig.builder()
      .economies(economies)
      .globalDiscount(globalDiscount)
      .discounts(discounts)
      .build();
  }

  /**
   * Returns an immutable snapshot of the conditions / behavior configuration.
   */
  public ConditionsConfig toConditionsConfig() {
    return ConditionsConfig.builder()
      .openConditions(openConditions)
      .closeCommand(closeCommand)
      .announceRotation(announceRotation)
      .build();
  }

  /**
   * Returns an immutable snapshot of the sound configuration.
   */
  public SoundConfig toSoundConfig() {
    return SoundConfig.builder()
      .soundOpen(soundOpen)
      .soundClose(soundClose)
      .build();
  }

  // --- Private helpers ---

  private void validateUniqueProductUuids() {
    Set<UUID> seen = new HashSet<>();
    for (Product product : products) {
      if (product.getUuid() != null && !seen.add(product.getUuid())) {
        UltraShop.LOGGER.warn("Duplicate product UUID: " + product.getUuid() + " in shop " + id + ". Regenerating.");
        product.setUuid(UUID.randomUUID());
        seen.add(product.getUuid());
      }
    }
  }

  private List<Product> defaultProducts() {
    List<Product> products = new ArrayList<>();
    for (ItemChance itemChance : ItemChance.defaultItemChances()) {
      Product p = new Product();
      p.setProduct(itemChance.getItem());
      p.setDisplay(itemChance.getDisplay());
      products.add(p);
    }
    products.add(new Product(true));
    return products;
  }
}
