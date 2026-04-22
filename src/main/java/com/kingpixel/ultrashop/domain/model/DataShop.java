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
   * Only operates on shops with {@link ShopType#ROTATION}.
   */
  public List<Product> updateDynamicProducts(Shop shop, String modId, boolean force) {
    if (!shop.isRotation() || shop.getRotationSchedule() == null) {
      return shop.getProducts();
    }

    products.computeIfAbsent(modId, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicRotation());

    DynamicRotation rotation = products.get(modId).get(shop.getId());

    boolean needsUpdate = rotation.getTimeToUpdate() < System.currentTimeMillis()
      || rotation.getProducts().isEmpty()
      || rotation.getProducts().size() != shop.getRotationSchedule().getAmount()
      || isScheduleStale(shop, rotation)
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

        rotation.setTimeToUpdate(computeNextFireTime(shop));

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
   * Resolves the next rotation timestamp for a shop.
   *
   * <p>Priority order:</p>
   * <ol>
   *   <li>{@code rotationSchedule.cron} (if set and parses successfully)</li>
   *   <li>{@code rotationSchedule.interval} (relative duration, e.g. "12h")</li>
   *   <li>Fallback: 1 hour from now</li>
   * </ol>
   */
  private long computeNextFireTime(Shop shop) {
    RotationSchedule sched = shop.getRotationSchedule();
    long now = System.currentTimeMillis();

    String cron = sched.getCron();
    if (cron != null && !cron.isBlank()) {
      try {
        long next = CronExpression.parse(cron).nextFireTime(now);
        UltraShop.LOGGER.info("Shop '" + shop.getId() + "' next rotation (cron '" + cron + "'): "
          + java.time.Instant.ofEpochMilli(next));
        return next;
      } catch (Exception e) {
        UltraShop.LOGGER.warn("Invalid cron '" + cron + "' for shop '" + shop.getId()
          + "': " + e.getMessage() + " — falling back to interval.");
      }
    }

    String interval = sched.getInterval();
    if (interval != null && !interval.isBlank()) {
      try {
        long ms = DurationValue.parse(interval).toMillis();
        if (ms > 0) {
          return now + ms;
        }
        UltraShop.LOGGER.warn("Interval '" + interval + "' for shop '" + shop.getId()
          + "' parsed as 0ms — using 1h fallback.");
      } catch (Exception e) {
        UltraShop.LOGGER.warn("Invalid interval '" + interval + "' for shop '" + shop.getId()
          + "': " + e.getMessage() + " — using 1h fallback.");
      }
    }

    return now + 3_600_000L; // 1 hour
  }

  /**
   * Detects when the persisted {@code timeToUpdate} no longer matches the current
   * shop schedule. Catches the case where the user changed the shop config from
   * {@code interval} to {@code cron} (or shortened the cron) but the old
   * timestamp persisted on disk is still pointing far into the future.
   *
   * <p>If the persisted {@code timeToUpdate} is <b>after</b> the next valid cron
   * fire-time, the schedule changed under our feet and we must recompute now.</p>
   */
  private boolean isScheduleStale(Shop shop, DynamicRotation rotation) {
    RotationSchedule sched = shop.getRotationSchedule();
    String cron = sched.getCron();
    if (cron == null || cron.isBlank()) {
      // No cron — only the interval path can produce stale state, but interval is
      // always relative to the moment of computation, so it can't go stale on its own.
      return false;
    }
    try {
      long expectedNext = CronExpression.parse(cron).nextFireTime(System.currentTimeMillis());
      // Tolerance of 60s to absorb clock drift between server restart and first call
      if (rotation.getTimeToUpdate() > expectedNext + 60_000L) {
        UltraShop.LOGGER.info("Shop '" + shop.getId() + "' has stale rotation timestamp ("
          + java.time.Instant.ofEpochMilli(rotation.getTimeToUpdate())
          + ") beyond next cron fire (" + java.time.Instant.ofEpochMilli(expectedNext)
          + "). Forcing recompute.");
        return true;
      }
    } catch (Exception ignored) {
      // Bad cron — handled later by computeNextFireTime fallback.
    }
    return false;
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

