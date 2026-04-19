package com.kingpixel.ultrashop.infrastructure.config;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.infrastructure.persistence.RepositoryFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    ctx.getSellIndex().rebuild(ctx.getShops());

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
   */
  public static void loadShops(ShopOptionsApi options) {
    ShopContext ctx = ShopContext.get();
    Path shopDir = CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");

    try {
      if (!Files.exists(shopDir)) {
        Files.createDirectories(shopDir);
        createDefaultShops(shopDir);
      }

      List<Shop> shops = new ArrayList<>();
      List<Path> jsonFiles = UtilsFile.getAllJsonFiles(shopDir);

      for (Path file : jsonFiles) {
        try {
          Shop shop = UtilsFile.read(file, Shop.class);
          if (shop == null) continue;

          shop.setId(file.getFileName().toString().replace(".json", ""));
          shop.check();

          // Write back (fills in new fields with defaults)
          shop.setFilePath(null); // Don't serialize file path
          UtilsFile.write(file, shop);
          shop.setFilePath(file.toString());

          shops.add(shop);
        } catch (Exception e) {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading shop " + file + ": " + e.getMessage());
        }
      }

      ctx.getShops().put(options.getModId(), shops);
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading shops: " + e.getMessage());
      ctx.getShops().put(options.getModId(), new ArrayList<>());
    }
  }

  /**
   * Saves a single shop to disk.
   */
  public static void saveShop(Shop shop) {
    if (shop.getFilePath() == null) return;
    Path path = Path.of(shop.getFilePath());
    UtilsFile.writeAsync(path, shop)
      .exceptionally(e -> {
        UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error saving shop " + shop.getId() + ": " + e.getMessage());
        return null;
      });
  }

  /**
   * Creates a shop and adds it to the registry.
   */
  public static void createShop(ShopOptionsApi options, Shop shop) {
    shop.check();
    Path shopDir = CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");
    Path filePath = shopDir.resolve(shop.getId() + ".json");
    try {
      UtilsFile.write(filePath, shop);
      shop.setFilePath(filePath.toString());
      load(options); // Reload everything
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error creating shop: " + e.getMessage());
    }
  }

  // --- Default shop generation ---

  private static void createDefaultShops(Path shopDir) {
    List<Shop> defaults = new ArrayList<>();

    // Permanent shop (static)
    Shop blocks = new Shop("blocks", false);
    blocks.getDisplay().setDisplayname("§6Block Shop");
    blocks.getDisplay().setLore(List.of("§7Buy your everyday building blocks here!"));
    defaults.add(blocks);

    // Dynamic shop (rotating products)
    Shop legendary = new Shop("legendary_rotation", true);
    legendary.getDisplay().setDisplayname("§eLegendary Rotation");
    legendary.getDisplay().setLore(List.of("§cExclusive items matching a 12h rotation!"));
    legendary.setRotationSchedule(new com.kingpixel.ultrashop.domain.model.RotationSchedule("12h", 2));
    defaults.add(legendary);

    // Category shop
    Shop categories = new Shop("categories", false);
    categories.getDisplay().setDisplayname("§bMain Menu");
    categories.setSubShops(List.of(
      new SubShop(10, "blocks"),
      new SubShop(11, "legendary_rotation")
    ));
    categories.setProducts(new ArrayList<>());
    defaults.add(categories);

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

        ## Dynamic Rotations

        Set `rotationSchedule` on a shop to enable rotating products:

        ### Using interval (relative cooldown):
        ```json
        "rotationSchedule": {
          "interval": "12h",
          "amount": 3
        }
        ```
        Supported intervals: `30m`, `1h`, `6h`, `12h`, `24h`, `7d`, etc.

        ### Using cron (fixed schedule):
        ```json
        "rotationSchedule": {
          "cron": "0 18 * * 5",
          "amount": 3
        }
        ```

        When `cron` is set, it **overrides** `interval`. The cron expression follows standard format:
        `minute hour day-of-month month day-of-week`.

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

