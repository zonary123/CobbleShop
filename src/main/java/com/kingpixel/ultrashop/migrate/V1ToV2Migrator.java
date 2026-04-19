package com.kingpixel.ultrashop.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Shop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrates v1 shop files (with ShopType hierarchy) to v2 format
 * (with dynamic flag + openConditions).
 *
 * <p>V1 format has a "type" field like: {@code {"typeShop": "DYNAMIC_WEEKLY", "cooldown": "30m", ...}}</p>
 * <p>V2 format has: {@code {"dynamic": true, "dynamicCooldown": "30m", "openConditions": [...]}}</p>
 *
 * <p>Detection: if a shop JSON has a "type" field with a "typeShop" inside, it's v1.</p>
 */
public final class V1ToV2Migrator {


  private V1ToV2Migrator() {
  }

  /**
   * Scans shop directory and migrates v1 files to v2.
   * Creates backup before any modification.
   */
  public static void migrateIfNeeded(Path shopDir) {
    if (!Files.exists(shopDir)) return;

    List<Path> jsonFiles = UtilsFile.getAllJsonFiles(shopDir);
    boolean anyMigrated = false;

    for (Path file : jsonFiles) {
      try {
        String content = UtilsFile.readText(file);
        JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

        if (isV1Format(json)) {
          UltraShop.LOGGER.info(UltraShop.MOD_ID, "Migrating v1 shop: " + file.getFileName());

          // Backup
          Path backupDir = shopDir.resolve("backup_v1");
          Files.createDirectories(backupDir);
          Files.copy(file, backupDir.resolve(file.getFileName()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

          // Migrate
          migrateShopJson(json);

          // Write back
          UtilsFile.writeText(file, UtilsFile.getGson().toJson(json));
          anyMigrated = true;
        }
      } catch (Exception e) {
        UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error migrating " + file + ": " + e.getMessage());
      }
    }

    if (anyMigrated) {
      UltraShop.LOGGER.info(UltraShop.MOD_ID, "V1 → V2 migration complete. Backups in shop/backup_v1/");
    }
  }

  /**
   * Detects v1 format by checking for the "type" field with "typeShop" inside.
   */
  private static boolean isV1Format(JsonObject json) {
    JsonElement typeElement = json.get("type");
    if (typeElement == null || !typeElement.isJsonObject()) return false;
    return typeElement.getAsJsonObject().has("typeShop");
  }

  /**
   * Converts a v1 shop JSON to v2 format in-place.
   */
  private static void migrateShopJson(JsonObject json) {
    JsonObject typeObj = json.getAsJsonObject("type");
    String typeShop = typeObj.get("typeShop").getAsString();

    // Determine if dynamic
    boolean isDynamic = typeShop.contains("DYNAMIC");
    json.addProperty("dynamic", isDynamic);

    // Extract dynamic fields
    if (isDynamic) {
      if (typeObj.has("cooldown")) {
        JsonElement cooldown = typeObj.get("cooldown");
        json.addProperty("dynamicCooldown", cooldown.isJsonPrimitive()
          ? cooldown.getAsString() : "30m");
      } else {
        json.addProperty("dynamicCooldown", "30m");
      }
      json.addProperty("productsRotation",
        typeObj.has("productsRotation") ? typeObj.get("productsRotation").getAsInt() : 3);
    }

    // Convert conditions
    JsonArray conditions = new JsonArray();

    // Weekly days → PermissionCondition (since there's no built-in DayOfWeek condition in CobbleUtils)
    // We keep this as metadata that the server admin can convert to custom conditions
    if (typeShop.contains("WEEKLY") && typeObj.has("days")) {
      // Store as a comment-like property for admin reference
      json.add("_legacyDays", typeObj.get("days"));
    }

    // Calendar date ranges → stored as legacy for admin reference
    if (typeShop.contains("CALENDAR") && typeObj.has("dateRanges")) {
      json.add("_legacyDateRanges", typeObj.get("dateRanges"));
    }

    json.add("openConditions", conditions);

    // Remove old type field
    json.remove("type");
  }

  /**
   * Also migrates the old OldShop format (v0 → v2) if files exist in the migration folder.
   */
  public static void migrateV0IfNeeded(Path migrationDir, Path shopDir) {
    if (!Files.exists(migrationDir)) return;

    try {
      List<Path> files = UtilsFile.getAllJsonFiles(migrationDir);
      for (Path file : files) {
        try {
          // Read as OldShop, convert to new Shop
          com.kingpixel.ultrashop.migrate.OldShop oldShop = UtilsFile.read(file, OldShop.class);
          if (oldShop == null) continue;

          Shop newShop = convertOldShop(oldShop);

          // Write to shop dir
          UtilsFile.write(shopDir.resolve(file.getFileName()), newShop);

          // Backup
          Path backupDir = migrationDir.resolve("backup_v0");
          Files.createDirectories(backupDir);
          Files.move(file, backupDir.resolve(file.getFileName()),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

          UltraShop.LOGGER.info(UltraShop.MOD_ID, "Migrated v0 shop: " + file.getFileName());
        } catch (Exception e) {
          UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error migrating v0 " + file + ": " + e.getMessage());
        }
      }
    } catch (Exception e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error scanning migration dir: " + e.getMessage());
    }
  }

  private static Shop convertOldShop(OldShop oldShop) {
    Shop shop = new Shop();
    shop.setId(oldShop.getId());
    shop.setTitle(oldShop.getTitle());
    shop.setRows(oldShop.getRows());
    shop.setAutoPlace(true);
    if (oldShop.getSoundopen() != null) shop.setSoundOpen(oldShop.getSoundopen());
    if (oldShop.getSoundclose() != null) shop.setSoundClose(oldShop.getSoundclose());
    if (oldShop.getCloseCommand() != null) shop.setCloseCommand(oldShop.getCloseCommand());
    shop.setGlobalDiscount(oldShop.getGlobalDiscount());
    if (oldShop.getRectangle() != null) shop.setRectangle(oldShop.getRectangle());
    if (oldShop.getDisplay() != null) shop.setDisplay(oldShop.getDisplay());

    // Convert products
    if (oldShop.getProducts() != null) {
      List<com.kingpixel.ultrashop.domain.model.Product> products = new ArrayList<>();
      for (OldProduct oldProduct : oldShop.getProducts()) {
        products.add(oldProduct.from());
      }
      shop.setProducts(products);
    }

    shop.check();
    return shop;
  }
}

