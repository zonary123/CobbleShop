package com.kingpixel.ultrashop.infrastructure.config;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.Model.conditions.PermissionCondition;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.cobbleutils.util.economys.providers.ImpactorEconomy;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.PriceEntry;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.model.shop.ShopBridge;
import com.kingpixel.ultrashop.infrastructure.persistence.RepositoryFactory;
import com.kingpixel.ultrashop.infrastructure.serialization.GsonProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Loads and saves all configuration and shop files using UtilsFile.
 * No GUI logic — pure infrastructure.
 */
public final class ConfigLoader {


  private ConfigLoader() {
  }

  /**
   * Full load: config → lang → shops → repositories → sell index → README.
   */
  public static void load(ShopOptionsApi options) {
    ShopContext ctx = ShopContext.get();

    // 1. Load config
    ShopConfig config = loadConfig(options);
    ctx.getConfigs().put(options.getModId(), config);
    options.setCommands(config.getCommands());

    // 2. Load lang (only for main mod)
    if (options.getModId().equals(UltraShop.MOD_ID)) {
      loadLang(config);
    }

    // 3. Load shops
    loadShops(options);

    // 4. Initialize repositories
    ctx.setRepositories(new RepositoryFactory(config.getDataBase()));

    // 5. Initialize DataShop
    ctx.getDataShop().init();

    // 6. Rebuild sell index
    ctx.getSellIndex().rebuild(ctx.getTypedShops());

    // 7. Always regenerate README
    generateReadme(CobbleUtils.getPath().resolve(options.getPath()));
  }

  /**
   * Loads config.json using UtilsFile.readOrCreate.
   */
  public static ShopConfig loadConfig(ShopOptionsApi options) {
    Path configPath = CobbleUtils.getPath().resolve(options.getPath()).resolve("config.json");
    try {
      ShopConfig config = UtilsFile.readOrCreate(configPath, ShopConfig.class, ShopConfig::new);
      config.check();
      UtilsFile.writeAsync(configPath, config);
      return config;
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading config: " + e.getMessage());
      ShopConfig fallback = new ShopConfig();
      fallback.check();
      return fallback;
    }
  }

  /**
   * Loads language file using UtilsFile.readOrCreate.
   */
  public static void loadLang(ShopConfig config) {
    ShopContext ctx = ShopContext.get();
    Path langPath = CobbleUtils.getPath().resolve(UltraShop.MOD_ID).resolve("lang").resolve(config.getLang() + ".json");
    try {
      LangConfig lang = UtilsFile.readOrCreate(langPath, LangConfig.class, LangConfig::new);
      UtilsFile.writeAsync(langPath, lang);
      ctx.setLang(lang);
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading lang: " + e.getMessage());
      ctx.setLang(new LangConfig());
    }
  }

  /**
   * Loads all shop files recursively from the shop directory.
   *
   * <p>Reads each file via {@link GsonProvider} which transparently handles both
   * the new {@code type}-discriminated format AND the legacy flat format (auto-bridged).
   * After load, every shop is rewritten in the canonical typed format, so legacy
   * configs migrate to the new shape on first boot without manual intervention.</p>
   *
   * <p>The legacy {@code ctx.getShops()} mirror is kept in sync via
   * {@link ShopBridge#toLegacy(com.kingpixel.ultrashop.domain.model.shop.Shop)}
   * to keep the editor (which still mutates the legacy view) functional until
   * its dedicated migration sub-phase lands.</p>
   */
  public static void loadShops(ShopOptionsApi options) {
    ShopContext ctx = ShopContext.get();
    Path shopDir = CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");

    try {
      if (!Files.exists(shopDir)) {
        Files.createDirectories(shopDir);
        createDefaultShops(shopDir);
      }

      List<com.kingpixel.ultrashop.domain.model.shop.Shop> typedShops = new ArrayList<>();
      List<Shop> legacyShops = new ArrayList<>();
      List<Path> jsonFiles = UtilsFile.getAllJsonFiles(shopDir);

      for (Path file : jsonFiles) {
        try {
          String json = Files.readString(file);
          com.kingpixel.ultrashop.domain.model.shop.Shop typed = GsonProvider.gson()
            .fromJson(json, com.kingpixel.ultrashop.domain.model.shop.Shop.class);
          if (typed == null) continue;

          String shopId = file.getFileName().toString().replace(".json", "");
          if (typed instanceof com.kingpixel.ultrashop.domain.model.shop.AbstractShop a) {
            a.setId(shopId);
          }

          // Rewrite in canonical typed format (migrates legacy files in-place).
          Files.writeString(file, GsonProvider.gson()
            .toJson(typed, com.kingpixel.ultrashop.domain.model.shop.Shop.class));

          // Mirror to legacy view for the editor and check() side-effects.
          Shop legacy = ShopBridge.toLegacy(typed);
          legacy.setFilePath(file.toString());
          legacy.check();

          typedShops.add(typed);
          legacyShops.add(legacy);
        } catch (Exception e) {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading shop " + file + ": " + e.getMessage());
        }
      }

      ctx.getShops().put(options.getModId(), legacyShops);
      ctx.getTypedShops().put(options.getModId(), typedShops);
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading shops: " + e.getMessage());
      ctx.getShops().put(options.getModId(), new ArrayList<>());
      ctx.getTypedShops().put(options.getModId(), new ArrayList<>());
    }
  }

  /**
   * Saves a single shop to disk in the canonical typed JSON format.
   *
   * <p>Accepts the legacy {@link Shop} type because the editor still operates on
   * the legacy view. Internally bridges to the typed hierarchy before writing,
   * guaranteeing the on-disk format stays in the new shape regardless of caller.</p>
   */
  public static void saveShop(Shop shop) {
    if (shop.getFilePath() == null) return;
    Path path = Path.of(shop.getFilePath());
    com.kingpixel.ultrashop.domain.model.shop.Shop typed;
    try {
      typed = ShopBridge.fromLegacy(shop);
    } catch (Exception e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID,
        "Error bridging shop " + shop.getId() + " before save: " + e.getMessage());
      return;
    }

    CompletableFuture.runAsync(() -> {
      try {
        Files.writeString(path, GsonProvider.gson()
          .toJson(typed, com.kingpixel.ultrashop.domain.model.shop.Shop.class));
      } catch (IOException e) {
        UltraShop.LOGGER.error(UltraShop.MOD_ID,
          "Error saving shop " + shop.getId() + ": " + e.getMessage());
      }
    });
  }

  /**
   * Creates a shop and adds it to the registry.
   *
   * <p>Persists in the canonical typed JSON format via {@link ShopBridge} +
   * {@link GsonProvider}.</p>
   */
  public static void createShop(ShopOptionsApi options, Shop shop) {
    shop.check();
    Path shopDir = CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");
    Path filePath = shopDir.resolve(shop.getId() + ".json");
    try {
      Files.createDirectories(shopDir);
      com.kingpixel.ultrashop.domain.model.shop.Shop typed = ShopBridge.fromLegacy(shop);
      Files.writeString(filePath, GsonProvider.gson()
        .toJson(typed, com.kingpixel.ultrashop.domain.model.shop.Shop.class));
      shop.setFilePath(filePath.toString());
      load(options); // Reload everything
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error creating shop: " + e.getMessage());
    }
  }

  // --- Default shop generation ---

  /**
   * Creates a comprehensive set of example shops covering all {@link ShopType} values
   * and the most common features. Each example targets a single concept so users can
   * compare side-by-side and learn by editing real, working configs.
   *
   * <p>Generated shops:</p>
   * <ul>
   *   <li>{@code main_menu} (CATEGORY) — entry point linking to all examples</li>
   *   <li>{@code starter_blocks} (NORMAL) — minimal buy-only shop</li>
   *   <li>{@code farm_market} (NORMAL) — sell-only farming products</li>
   *   <li>{@code tools_workshop} (NORMAL) — fixed-slot layout, no autoPlace</li>
   *   <li>{@code vip_lounge} (NORMAL) — restricted by permission via openConditions</li>
   *   <li>{@code limited_drops} (NORMAL) — per-player buy limits with cooldown</li>
   *   <li>{@code multicurrency_bazaar} (NORMAL) — products priced in multiple economies</li>
   *   <li>{@code hourly_rotation} (ROTATION, interval) — rotates every hour</li>
   *   <li>{@code legendary_rotation} (ROTATION, cron weekly) — Friday 18:00</li>
   *   <li>{@code daily_specials} (ROTATION, cron daily) — every midnight</li>
   * </ul>
   */
  private static void createDefaultShops(Path shopDir) {
    List<Shop> defaults = new ArrayList<>();

    defaults.add(buildStarterBlocks());
    defaults.add(buildFarmMarket());
    defaults.add(buildToolsWorkshop());
    defaults.add(buildVipLounge());
    defaults.add(buildLimitedDrops());
    defaults.add(buildMulticurrencyBazaar());
    defaults.add(buildHourlyRotation());
    defaults.add(buildLegendaryRotation());
    defaults.add(buildDailySpecials());
    defaults.add(buildMainMenu()); // last so subShops references resolve in editor view

    int slot = 0;
    for (Shop shop : defaults) {
      if (shop.getDisplay().getSlot() == 0) {
        shop.getDisplay().setSlot(slot++);
      }
      shop.check();
      try {
        UtilsFile.write(shopDir.resolve(shop.getId() + ".json"), shop);
      } catch (IOException e) {
        UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error creating default shop: " + e.getMessage());
      }
    }
  }

  // --- Default shop builders ---

  /** CATEGORY: top-level menu pointing to every example shop. */
  private static Shop buildMainMenu() {
    Shop shop = new Shop("main_menu", ShopType.CATEGORY);
    shop.setName("Main Menu");
    shop.getDisplay().setDisplayname("§b§lMain Menu");
    shop.getDisplay().setLore(List.of(
      "§7Browse all shops by category.",
      "§7This is a §bCATEGORY§7 shop — it has no products,",
      "§7only links to other shops via §fsubShops§7."
    ));
    shop.setSubShops(List.of(
      new SubShop(10, "starter_blocks"),
      new SubShop(11, "farm_market"),
      new SubShop(12, "tools_workshop"),
      new SubShop(13, "limited_drops"),
      new SubShop(14, "multicurrency_bazaar"),
      new SubShop(15, "vip_lounge"),
      new SubShop(16, "hourly_rotation"),
      new SubShop(20, "legendary_rotation"),
      new SubShop(22, "daily_specials")
    ));
    shop.setProducts(new ArrayList<>());
    return shop;
  }

  /** NORMAL: minimal buy-only shop — the simplest possible configuration. */
  private static Shop buildStarterBlocks() {
    Shop shop = new Shop("starter_blocks", ShopType.NORMAL);
    shop.setName("Starter Blocks");
    shop.getDisplay().setDisplayname("§a§lStarter Blocks");
    shop.getDisplay().setLore(List.of(
      "§7Cheap building blocks for new players.",
      "§7§oNORMAL shop — every product is always visible."
    ));
    shop.setProducts(new ArrayList<>(List.of(
      simpleProduct("minecraft:dirt", 5, 1),
      simpleProduct("minecraft:cobblestone", 8, 2),
      simpleProduct("minecraft:oak_planks", 10, 2),
      simpleProduct("minecraft:stone", 12, 3),
      simpleProduct("minecraft:glass", 15, 4),
      simpleProduct("minecraft:torch", 5, 0)
    )));
    return shop;
  }

  /** NORMAL: sell-focused — buy=0 disables purchases, only selling is allowed. */
  private static Shop buildFarmMarket() {
    Shop shop = new Shop("farm_market", ShopType.NORMAL);
    shop.setName("Farm Market");
    shop.getDisplay().setDisplayname("§e§lFarm Market");
    shop.getDisplay().setLore(List.of(
      "§7Sell your harvest here for a fair price.",
      "§7§oSet §fbuy=0§7 to make a product §fsell-only§7."
    ));
    shop.setProducts(new ArrayList<>(List.of(
      simpleProduct("minecraft:wheat", 0, 4),
      simpleProduct("minecraft:carrot", 0, 5),
      simpleProduct("minecraft:potato", 0, 5),
      simpleProduct("minecraft:beetroot", 0, 6),
      simpleProduct("minecraft:pumpkin", 0, 10),
      simpleProduct("minecraft:melon", 0, 8),
      simpleProduct("minecraft:apple", 0, 12),
      simpleProduct("minecraft:sweet_berries", 0, 7)
    )));
    return shop;
  }

  /** NORMAL: fixed-slot layout — autoPlace=false + slot per product. */
  private static Shop buildToolsWorkshop() {
    Shop shop = new Shop("tools_workshop", ShopType.NORMAL);
    shop.setName("Tools Workshop");
    shop.getDisplay().setDisplayname("§6§lTools Workshop");
    shop.getDisplay().setLore(List.of(
      "§7Buy tools at fixed positions in the GUI.",
      "§7§oautoPlace=false §7lets you pin each product to a §fslot§7."
    ));
    shop.setAutoPlace(false);
    shop.setProducts(new ArrayList<>(List.of(
      slottedProduct("minecraft:wooden_pickaxe", 50, 0, 10),
      slottedProduct("minecraft:stone_pickaxe", 150, 0, 11),
      slottedProduct("minecraft:iron_pickaxe", 500, 0, 12),
      slottedProduct("minecraft:diamond_pickaxe", 2500, 0, 13),
      slottedProduct("minecraft:netherite_pickaxe", 10000, 0, 14),
      slottedProduct("minecraft:wooden_axe", 50, 0, 19),
      slottedProduct("minecraft:stone_axe", 150, 0, 20),
      slottedProduct("minecraft:iron_axe", 500, 0, 21),
      slottedProduct("minecraft:diamond_axe", 2500, 0, 22),
      slottedProduct("minecraft:netherite_axe", 10000, 0, 23)
    )));
    return shop;
  }

  /** NORMAL: gated by openConditions — requires the permission node to even open the shop. */
  private static Shop buildVipLounge() {
    Shop shop = new Shop("vip_lounge", ShopType.NORMAL);
    shop.setName("VIP Lounge");
    shop.getDisplay().setDisplayname("§d§lVIP Lounge");
    shop.getDisplay().setLore(List.of(
      "§7Exclusive items for VIP players.",
      "§7§oUses §fopenConditions §7to require a permission to open."
    ));
    List<Condition> openConditions = new ArrayList<>();
    openConditions.add(PermissionCondition.builder().permission("ultrashop.vip").build());
    shop.setOpenConditions(openConditions);
    shop.setGlobalDiscount(15.0f); // 15% discount for VIPs
    shop.setProducts(new ArrayList<>(List.of(
      simpleProduct("minecraft:netherite_ingot", 8000, 4000),
      simpleProduct("minecraft:elytra", 50000, 25000),
      simpleProduct("minecraft:totem_of_undying", 12000, 6000),
      simpleProduct("minecraft:enchanted_golden_apple", 5000, 2500),
      simpleProduct("minecraft:beacon", 30000, 15000)
    )));
    return shop;
  }

  /** NORMAL: per-player buy limits via max + cooldown (auto-generates UUID). */
  private static Shop buildLimitedDrops() {
    Shop shop = new Shop("limited_drops", ShopType.NORMAL);
    shop.setName("Limited Drops");
    shop.getDisplay().setDisplayname("§c§lLimited Drops");
    shop.getDisplay().setLore(List.of(
      "§7Each player can buy a limited amount per cooldown.",
      "§7§oSet §fmax §7and §fcooldown §7(minutes) on the product."
    ));

    Product dailyDiamond = simpleProduct("minecraft:diamond", 100, 50);
    dailyDiamond.setMax(8);
    dailyDiamond.setCooldown(1440); // 24h

    Product hourlyEnderPearl = simpleProduct("minecraft:ender_pearl", 200, 100);
    hourlyEnderPearl.setMax(4);
    hourlyEnderPearl.setCooldown(60); // 1h

    Product weeklyTotem = simpleProduct("minecraft:totem_of_undying", 5000, 2500);
    weeklyTotem.setMax(1);
    weeklyTotem.setCooldown(10080); // 7d

    shop.setProducts(new ArrayList<>(List.of(dailyDiamond, hourlyEnderPearl, weeklyTotem)));
    return shop;
  }

  /** NORMAL: products priced in multiple currencies via the {@code prices} array. */
  private static Shop buildMulticurrencyBazaar() {
    Shop shop = new Shop("multicurrency_bazaar", ShopType.NORMAL);
    shop.setName("Bazaar");
    shop.getDisplay().setDisplayname("§9§lMulti-Currency Bazaar");
    shop.getDisplay().setLore(List.of(
      "§7Pay with §fmultiple currencies §7at once.",
      "§7§oWhen §fprices[] §7is set, simple §fbuy/sell §7are ignored."
    ));

    EconomyUse dollars = new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars");
    EconomyUse diamonds = new EconomyUse("item", "minecraft:diamond");
    EconomyUse emeralds = new EconomyUse("item", "minecraft:emerald");

    // Shop default economies (used by simple buy/sell products in this shop)
    shop.setEconomies(new LinkedHashSet<>(List.of(dollars)));

    Product netheriteSword = simpleProduct("minecraft:netherite_sword", 0, 0);
    netheriteSword.setPrices(new ArrayList<>(List.of(
      new PriceEntry(dollars, BigDecimal.valueOf(2000), BigDecimal.valueOf(1000)),
      new PriceEntry(diamonds, BigDecimal.valueOf(8), BigDecimal.valueOf(4))
    )));

    Product enchantedBook = simpleProduct("minecraft:enchanted_book", 0, 0);
    enchantedBook.setPrices(new ArrayList<>(List.of(
      new PriceEntry(emeralds, BigDecimal.valueOf(16), BigDecimal.valueOf(8))
    )));

    Product shulkerBox = simpleProduct("minecraft:shulker_box", 0, 0);
    shulkerBox.setPrices(new ArrayList<>(List.of(
      new PriceEntry(dollars, BigDecimal.valueOf(5000), BigDecimal.ZERO),
      new PriceEntry(diamonds, BigDecimal.valueOf(20), BigDecimal.ZERO),
      new PriceEntry(emeralds, BigDecimal.valueOf(64), BigDecimal.ZERO)
    )));

    shop.setProducts(new ArrayList<>(List.of(netheriteSword, enchantedBook, shulkerBox)));
    return shop;
  }

  /** ROTATION (interval): a small subset of the pool, refreshed every hour. */
  private static Shop buildHourlyRotation() {
    Shop shop = new Shop("hourly_rotation", ShopType.ROTATION);
    shop.setName("Hourly Rotation");
    shop.getDisplay().setDisplayname("§b§lHourly Rotation");
    shop.getDisplay().setLore(List.of(
      "§7Refreshes every hour.",
      "§7§oROTATION + §finterval='1h'§7 — relative cooldown."
    ));
    shop.setRotationSchedule(new RotationSchedule("1h", 4));
    shop.setAnnounceRotation(true);
    shop.setProducts(new ArrayList<>(List.of(
      weightedProduct("minecraft:redstone", 30, 15, 100),
      weightedProduct("minecraft:lapis_lazuli", 25, 12, 100),
      weightedProduct("minecraft:quartz", 35, 17, 80),
      weightedProduct("minecraft:glowstone_dust", 40, 20, 80),
      weightedProduct("minecraft:blaze_rod", 150, 75, 50),
      weightedProduct("minecraft:ghast_tear", 500, 250, 20),
      weightedProduct("minecraft:nether_star", 5000, 2500, 5)
    )));
    return shop;
  }

  /** ROTATION (cron): high-tier weekly drop — Fridays at 18:00 server time. */
  private static Shop buildLegendaryRotation() {
    Shop shop = new Shop("legendary_rotation", ShopType.ROTATION);
    shop.setName("Legendary Rotation");
    shop.getDisplay().setDisplayname("§6§lLegendary Rotation");
    shop.getDisplay().setLore(List.of(
      "§7High-tier items rotated §fevery Friday at 18:00§7.",
      "§7§oROTATION + §fcron='0 18 * * 5'§7 — fixed schedule."
    ));
    RotationSchedule sched = new RotationSchedule("7d", 2);
    sched.setCron("0 18 * * 5"); // Friday 18:00 — overrides interval
    shop.setRotationSchedule(sched);
    shop.setAnnounceRotation(true);
    shop.setProducts(new ArrayList<>(List.of(
      weightedProduct("minecraft:elytra", 25000, 10000, 30),
      weightedProduct("minecraft:netherite_block", 15000, 7500, 50),
      weightedProduct("minecraft:beacon", 20000, 10000, 40),
      weightedProduct("minecraft:dragon_egg", 100000, 50000, 5),
      weightedProduct("minecraft:trident", 8000, 4000, 60),
      weightedProduct("minecraft:enchanted_golden_apple", 3000, 1500, 80)
    )));
    return shop;
  }

  /** ROTATION (cron): daily refresh at midnight — common "daily deals" pattern. */
  private static Shop buildDailySpecials() {
    Shop shop = new Shop("daily_specials", ShopType.ROTATION);
    shop.setName("Daily Specials");
    shop.getDisplay().setDisplayname("§e§lDaily Specials");
    shop.getDisplay().setLore(List.of(
      "§7New deals every day at midnight.",
      "§7§oROTATION + §fcron='0 0 * * *'§7 — daily reset."
    ));
    RotationSchedule sched = new RotationSchedule("24h", 6);
    sched.setCron("0 0 * * *");
    shop.setRotationSchedule(sched);
    shop.setProducts(new ArrayList<>(List.of(
      weightedProduct("minecraft:iron_ingot", 50, 25, 100),
      weightedProduct("minecraft:gold_ingot", 80, 40, 100),
      weightedProduct("minecraft:copper_ingot", 30, 15, 100),
      weightedProduct("minecraft:emerald", 200, 100, 80),
      weightedProduct("minecraft:diamond", 300, 150, 60),
      weightedProduct("minecraft:experience_bottle", 100, 0, 70),
      weightedProduct("minecraft:saddle", 800, 400, 40),
      weightedProduct("minecraft:name_tag", 600, 300, 50)
    )));
    return shop;
  }

  // --- Product factory helpers ---

  private static Product simpleProduct(String id, double buy, double sell) {
    Product p = new Product();
    p.setProduct(id);
    p.setBuy(BigDecimal.valueOf(buy));
    p.setSell(BigDecimal.valueOf(sell));
    return p;
  }

  private static Product slottedProduct(String id, double buy, double sell, int slot) {
    Product p = simpleProduct(id, buy, sell);
    p.setSlot(slot);
    return p;
  }

  private static Product weightedProduct(String id, double buy, double sell, int chance) {
    Product p = simpleProduct(id, buy, sell);
    p.setChance(chance);
    return p;
  }

  /**
   * Generates (or regenerates) the README.md in the config root folder.
   * Called on every load to keep documentation up to date.
   */
  private static void generateReadme(Path configRoot) {
    try {
      Files.createDirectories(configRoot);
      Path readme = configRoot.resolve("README.md");
      String content = """
        # UltraShop Configuration

        Welcome to UltraShop! This folder contains all configuration files for your shop system.

        ## Directory Structure

        ```
        ultrashop/
        ├── config.json          # Core settings (database, commands, discounts, etc.)
        ├── lang/                # Language files (en.json, es.json, etc.)
        │   └── en.json
        ├── shop/                # Shop definitions — one .json per shop
        │   ├── blocks.json
        │   ├── legendary_rotation.json
        │   └── categories.json
        ├── data/
        │   ├── rotations/       # Dynamic shop rotation state (per modId/shopId)
        │   │   └── ultrashop/
        │   │       └── legendary_rotation.json
        │   ├── transactions/    # Transaction logs (one file per day)
        │   │   └── 2025-01-15.json
        │   └── users/           # Per-player data (buy limits, cooldowns)
        │       └── <uuid>.json
        └── README.md            # This file
        ```

        ## config.json Fields

        | Field | Type | Default | Description |
        |-------|------|---------|-------------|
        | `debug` | boolean | `false` | Enable debug logging |
        | `saveTransactions` | boolean | `true` | Record buy/sell transactions |
        | `lang` | string | `"en"` | Language file name (without .json) |
        | `rows` | int | `6` | GUI rows for the main menu |
        | `title` | string | `"Shop"` | Default GUI title |
        | `soundOpen` | string | `""` | Sound on GUI open |
        | `soundClose` | string | `""` | Sound on GUI close |
        | `commands` | string[] | `["shop","ultrashop"]` | Command aliases for the shop |
        | `sellCommands` | string[] | `["sell"]` | Command aliases for /sell |
        | `maxBuyAmount` | int | `2304` | Maximum items per buy transaction |
        | `transactionPageSize` | int | `10` | Entries shown in /shop transactions |
        | `discounts` | map | `{"group.vip": 2.0}` | Permission-based discount percentages |
        | `dataBase` | object | JSON | Database config (JSON, MySQL, SQLite) |

        ## Shop JSON Fields

        | Field | Type | Required | Description |
        |-------|------|----------|-------------|
        | `name` | string | yes | Display name of the shop |
        | `title` | string | no | GUI title (use `%shop%` placeholder) |
        | `autoPlace` | boolean | no | Auto-arrange products in grid |
        | `rows` | int | no | Number of GUI rows |
        | `economies` | EconomyUse[] | yes | Default currencies (used by products without `prices`) |
        | `products` | Product[] | yes | List of products |
        | `subShops` | SubShop[] | no | Category navigation links |
        | `rotationSchedule` | object | no | Dynamic rotation config (see below) |
        | `openConditions` | Condition[] | no | When the shop is accessible |
        | `announceRotation` | boolean | no | Broadcast rotation changes |
        | `globalDiscount` | float | no | Shop-wide discount percentage |
        | `discounts` | map | no | Permission-based discounts for this shop |

        ## Product JSON Fields

        All fields except `product`, `buy`, and `sell` are **nullable** (omit them if not needed).

        | Field | Type | Required | Description |
        |-------|------|----------|-------------|
        | `product` | string | yes | Item ID (`minecraft:stone`, `command:give %player% ...`, `pokemon:pikachu`) |
        | `buy` | decimal | yes | Buy price (0 = cannot buy). Used with shop's `economies` |
        | `sell` | decimal | yes | Sell price (0 = cannot sell). Used with shop's `economies` |
        | `prices` | PriceEntry[] | no | **Multi-currency override** — when set, `buy`/`sell` are ignored |
        | `display` | string | no | Override display item |
        | `displayname` | string | no | Custom display name |
        | `lore` | string[] | no | Custom lore lines |
        | `slot` | int | no | Fixed slot position (when autoPlace=false) |
        | `discount` | float | no | Per-product discount % |
        | `oneByOne` | boolean | no | Force stack size 1 |
        | `uuid` | UUID | no | Auto-generated for limited products |
        | `max` | int | no | Max purchases per cooldown period |
        | `cooldown` | int | no | Cooldown in minutes |
        | `chance` | int | no | Weight for rotation selection (default: 100) |
        | `conditions` | Condition[] | no | Conditions to buy |
        | `visibilityConditions` | Condition[] | no | Conditions to see the product |
        | `CustomModelData` | int | no | Custom model data for display item |

        ## Multi-Currency per Product

        By default, products use the simple `buy`/`sell` fields and the shop's `economies` list.
        For products that need different prices in different currencies, use the `prices` array:

        ### Simple product (most common — uses shop's economies):
        ```json
        {
          "product": "minecraft:diamond",
          "buy": 100,
          "sell": 50
        }
        ```

        ### Multi-currency product (optional — overrides buy/sell):
        ```json
        {
          "product": "minecraft:netherite_sword",
          "buy": 0,
          "sell": 0,
          "prices": [
            {
              "economy": { "type": "impactor", "currency": "impactor:dollars" },
              "buy": 500,
              "sell": 200
            },
            {
              "economy": { "type": "item", "currency": "minecraft:diamond" },
              "buy": 10,
              "sell": 5
            }
          ]
        }
        ```

        When `prices` is present and non-empty, the player must pay **ALL** listed currencies to buy,
        and receives **ALL** listed currencies when selling. The simple `buy`/`sell` fields are ignored.

        ### PriceEntry Fields

        | Field | Type | Description |
        |-------|------|-------------|
        | `economy` | EconomyUse | The economy provider (`type` + `currency`) |
        | `buy` | decimal | Buy price in this economy (0 = not charged) |
        | `sell` | decimal | Sell price in this economy (0 = not paid) |

        ## Shop Types

        Each shop has a `type` field with one of three values:

        | Value | Behavior |
        |-------|----------|
        | `NORMAL` | Static catalog. All `products` are always visible. `rotationSchedule` and `subShops` are ignored. |
        | `CATEGORY` | Menu shop. Shows the entries listed in `subShops` (which point to other shops by id). `products` and `rotationSchedule` are ignored. |
        | `ROTATION` | Dynamic catalog. A subset of `products` is rotated based on `rotationSchedule`. |

        ```json
        { "type": "ROTATION", "rotationSchedule": { "interval": "12h", "amount": 3 } }
        ```

        > **Back-compat:** if `type` is missing, it defaults to `NORMAL`. On load, if
        > `subShops` is non-empty it auto-promotes to `CATEGORY`; if `rotationSchedule`
        > is present it auto-promotes to `ROTATION`. Old configs keep working without
        > edits, but **adding `type` explicitly is strongly recommended**.

        ## Dynamic Rotations

        Set `type: "ROTATION"` and a `rotationSchedule` on a shop to enable rotating products:

        ### Using interval (relative cooldown):
        ```json
        "type": "ROTATION",
        "rotationSchedule": {
          "interval": "12h",
          "amount": 3
        }
        ```
        Supported intervals: `30m`, `1h`, `6h`, `12h`, `24h`, `7d`, etc.

        ### Using cron (fixed schedule):
        ```json
        "type": "ROTATION",
        "rotationSchedule": {
          "cron": "0 18 * * 5",
          "amount": 3
        }
        ```

        When `cron` is set, it **overrides** `interval`. The cron expression follows the standard 5-field format:
        `minute hour day-of-month month day-of-week`.

        Supported syntax: `*`, `n`, `a-b`, `a,b,c`, `*/n` (step). Day-of-week uses 0=Sunday..6=Saturday (7 also accepted as Sunday).

        Examples:
        - `0 18 * * 5` — every Friday at 18:00
        - `0 * * * *` — top of every hour
        - `*/15 * * * *` — every 15 minutes
        - `0 0,12 * * *` — at 00:00 and 12:00 every day
        - `0 9 1 * *` — at 09:00 on the 1st of every month

        **Cron examples:**
        | Expression | Description |
        |-----------|-------------|
        | `0 18 * * 5` | Every Friday at 18:00 |
        | `0 0 * * *` | Every day at midnight |
        | `0 12 * * 1,3,5` | Mon/Wed/Fri at noon |
        | `0 0 1 * *` | First day of each month at midnight |
        | `30 6 * * *` | Every day at 06:30 |

        ## Transaction History

        Players can view their purchase/sale history via GUI:
        - `/shop transactions` — Opens a paginated GUI with your transaction history
        - `/shop transactions <player>` (admin) — Opens GUI showing another player's transactions

        Each transaction shows: date, action (BUY/SELL), product, amount, price, and currency.

        ## Commands

        | Command | Permission | Description |
        |---------|-----------|-------------|
        | `/shop` | `ultrashop.base` | Open main shop menu |
        | `/shop reload` | `ultrashop.admin` | Reload all configuration |
        | `/shop create <id> <dynamic>` | `ultrashop.admin` | Create a new shop |
        | `/shop delete <id>` | `ultrashop.admin` | Delete a shop |
        | `/shop edit` | `ultrashop.admin` | Open in-game shop/product editor GUI |
        | `/shop other <player> [shopId]` | `ultrashop.admin` | Open shop for another player |
        | `/shop restartShop <id>` | `ultrashop.restart.shop` | Force rotation restart |
        | `/shop transactions [player]` | `ultrashop.transactions` | View transaction history (GUI) |
        | `/shop search <query>` | `ultrashop.search.base` | Search products across shops |
        | `/sell hand` | `ultrashop.sell.base` | Sell item in hand |
        | `/sell all` | `ultrashop.sell.base` | Sell all sellable items |


        ---
        *Auto-generated by UltraShop. This file is regenerated on every reload.*
        """;
      Files.writeString(readme, content);
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error generating README.md: " + e.getMessage());
    }
  }
}

