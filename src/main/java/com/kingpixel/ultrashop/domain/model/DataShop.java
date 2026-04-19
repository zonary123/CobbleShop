package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores the state of dynamic product rotations across all shops.
 * Each modId/shopId pair is stored in its own file under data/rotations/{modId}/{shopId}.json.
 */
@Data
public class DataShop {

  private ConcurrentMap<String, ConcurrentMap<String, DynamicRotation>> products = new ConcurrentHashMap<>();

  private static final Path BASE_PATH = CobbleUtils.getPath()
    .resolve(UltraShop.MOD_ID).resolve("data");

  private static final Path ROTATIONS_DIR = BASE_PATH.resolve("rotations");

  private static final Path LEGACY_FILE = BASE_PATH.resolve("dataShop.json");

  public void init() {
    try {
      migrateFromLegacy();
      loadAllRotations();
    } catch (Exception e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading DataShop: " + e.getMessage());
      this.products = new ConcurrentHashMap<>();
    }
  }

  /**
   * Migrates from the old single dataShop.json to per-shop files.
   */
  private void migrateFromLegacy() {
    if (!UtilsFile.exists(LEGACY_FILE)) return;

    try {
      DataShop legacy = UtilsFile.read(LEGACY_FILE, DataShop.class);
      if (legacy != null && legacy.products != null) {
        legacy.products.forEach((modId, shopMap) -> {
          shopMap.forEach((shopId, rotation) -> {
            Path file = ROTATIONS_DIR.resolve(modId).resolve(shopId + ".json");
            try {
              Files.createDirectories(file.getParent());
              UtilsFile.write(file, rotation);
            } catch (IOException e) {
              UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error migrating rotation " + modId + "/" + shopId + ": " + e.getMessage());
            }
          });
        });
        UltraShop.LOGGER.info("Migrated dataShop.json to per-shop rotation files.");
      }

      // Backup and delete legacy file
      Path backup = LEGACY_FILE.resolveSibling("dataShop.json.bak");
      Files.move(LEGACY_FILE, backup);
      UltraShop.LOGGER.info("Legacy dataShop.json backed up to dataShop.json.bak");
    } catch (Exception e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error during legacy migration: " + e.getMessage());
    }
  }

  /**
   * Loads all per-shop rotation files from data/rotations/{modId}/{shopId}.json.
   */
  private void loadAllRotations() {
    if (!Files.exists(ROTATIONS_DIR)) return;

    try (var dirs = Files.list(ROTATIONS_DIR)) {
      dirs.filter(Files::isDirectory)
        .forEach(modDir -> {
          String modId = modDir.getFileName().toString();
          ConcurrentMap<String, DynamicRotation> shopMap = new ConcurrentHashMap<>();
          try {
            List<Path> jsonFiles = UtilsFile.getAllJsonFiles(modDir);
            for (Path file : jsonFiles) {
              try {
                String shopId = file.getFileName().toString().replace(".json", "");
                DynamicRotation rotation = UtilsFile.read(file, DynamicRotation.class);
                if (rotation != null) {
                  shopMap.put(shopId, rotation);
                }
              } catch (Exception e) {
                UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error loading rotation " + file + ": " + e.getMessage());
              }
            }
          } catch (Exception e) {
            UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error scanning rotations for " + modId + ": " + e.getMessage());
          }
          if (!shopMap.isEmpty()) {
            products.put(modId, shopMap);
          }
        });
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error scanning rotations directory: " + e.getMessage());
    }
  }

  /**
   * Writes all rotation data to per-shop files.
   */
  public void write() {
    products.forEach((modId, shopMap) -> {
      shopMap.forEach((shopId, rotation) -> writeShopRotation(modId, shopId, rotation));
    });
  }

  /**
   * Writes a single shop's rotation data to disk.
   */
  private void writeShopRotation(String modId, String shopId, DynamicRotation rotation) {
    Path file = ROTATIONS_DIR.resolve(modId).resolve(shopId + ".json");
    try {
      Files.createDirectories(file.getParent());
    } catch (IOException e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error creating rotation dir: " + e.getMessage());
      return;
    }
    UtilsFile.writeAsync(file, rotation)
      .exceptionally(e -> {
        UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error writing rotation " + modId + "/" + shopId + ": " + e.getMessage());
        return null;
      });
  }

  /**
   * Updates dynamic products for a shop, rotating if cooldown expired.
   */
  public List<Product> updateDynamicProducts(Shop shop, String modId, boolean force) {
    products.computeIfAbsent(modId, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicRotation());

    DynamicRotation rotation = products.get(modId).get(shop.getId());

    boolean needsUpdate = rotation.getTimeToUpdate() < System.currentTimeMillis()
      || rotation.getProducts().isEmpty()
      || rotation.getProducts().size() != shop.getRotationSchedule().getAmount()
      || force;

    if (needsUpdate) {
      ShopContext ctx = ShopContext.get();
      ctx.getAsyncContext().runAsync(() -> {
        if (shop.isAnnounceRotation()) {
          PlayerUtils.sendMessage(
            (net.minecraft.server.network.ServerPlayerEntity) null,
            ctx.getLang().getMessageShopRotated().replace("%shop%", shop.getName()),
            ctx.getLang().getPrefix(),
            TypeMessage.BROADCAST
          );
        }

        long cooldownMs = DurationValue.parse(shop.getRotationSchedule().getInterval()).toMillis();
        rotation.setTimeToUpdate(System.currentTimeMillis() + cooldownMs);

        List<Product> shuffled = new ArrayList<>();
        List<Product> available = new ArrayList<>(shop.getProducts());
        java.util.Random rand = new java.util.Random();
        int amountNeeded = Math.min(shop.getRotationSchedule().getAmount(), available.size());

        for (int i = 0; i < amountNeeded && !available.isEmpty(); i++) {
          int totalWeight = available.stream().mapToInt(Product::getEffectiveChance).sum();
          if (totalWeight <= 0) break;
          int r = rand.nextInt(totalWeight);
          int current = 0;
          Product picked = null;
          for (Product p : available) {
            current += p.getEffectiveChance();
            if (current > r) {
              picked = p;
              break;
            }
          }
          if (picked != null) {
            shuffled.add(picked);
            available.remove(picked);
          }
        }
        rotation.setProducts(shuffled);

        writeShopRotation(modId, shop.getId(), rotation);

        // Rebuild sell index after rotation
        ctx.getSellIndex().rebuild(ctx.getShops());
      });
    }

    return rotation.getProducts();
  }

  /**
   * Returns the cooldown expiration timestamp for a shop's rotation.
   */
  public long getActualCooldown(Shop shop, String modId) {
    return products
      .computeIfAbsent(modId, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicRotation())
      .getTimeToUpdate();
  }
}

