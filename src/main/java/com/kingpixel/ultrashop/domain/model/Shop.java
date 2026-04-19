package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.*;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.util.economys.providers.ImpactorEconomy;
import com.kingpixel.ultrashop.UltraShop;
import lombok.Data;

import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A shop definition — pure domain model.
 * No GUI logic, no I/O, no serialization.
 *
 * <p>The old ShopType hierarchy (Permanent, Weekly, Calendar, Dynamic, etc.)
 * is replaced by two orthogonal concepts:</p>
 * <ul>
 *   <li>{@code dynamic} + {@code dynamicCooldown} + {@code productsRotation} — controls product rotation</li>
 *   <li>{@code openConditions} — controls when the shop is accessible (replaces Weekly/Calendar types)</li>
 * </ul>
 */
@Data
public class Shop {


  // --- Transient (not serialized) ---
  private transient String filePath;
  private transient String id;

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
  @NotNull private List<EconomyUse> economies;
  private float globalDiscount;
  @NotNull private Map<String, Float> discounts;

  // --- Behavior ---
  @Nullable private String closeCommand;
  private boolean announceRotation;

  // --- Dynamic Rotation Schedule ---
  @Nullable private RotationSchedule rotationSchedule;

  // --- Conditions ---
  @NotNull private List<Condition> openConditions;

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
    this.economies = new ArrayList<>(List.of(new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars")));
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
   * Creates a shop with a specific id and dynamic flag.
   */
  public Shop(String id, boolean hasRotation) {
    this();
    this.id = id;
    if (hasRotation) {
        this.rotationSchedule = new RotationSchedule("30m", 3);
    }
  }

  /**
   * Validates and fills in defaults for missing fields.
   */
  public void check() {
    if (subShops == null) subShops = new ArrayList<>();
    if (economies == null || economies.isEmpty()) {
      economies = new ArrayList<>();
      economies.add(new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars"));
    }
    if (openConditions == null) openConditions = new ArrayList<>();
    if (discounts == null) discounts = new HashMap<>();
    if (products == null) products = new ArrayList<>();
    if (panels == null) panels = List.of(new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows));

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
   */
  public boolean hasCategories() {
    return subShops != null && !subShops.isEmpty();
  }

  // --- Private helpers ---

  private void validateUniqueProductUuids() {
    Set<UUID> seen = new HashSet<>();
    for (Product product : products) {
      if (product.getUuid() != null) {
        if (!seen.add(product.getUuid())) {
          UltraShop.LOGGER.warn("Duplicate product UUID: " + product.getUuid() + " in shop " + id + ". Regenerating.");
          product.setUuid(UUID.randomUUID());
          seen.add(product.getUuid());
        }
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
