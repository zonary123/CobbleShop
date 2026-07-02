package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.infrastructure.webhook.DiscordWebhookHelper;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stores the state of dynamic product rotations across all shops.
 * Each modId/shopId pair is stored in its own file under data/rotations/{modId}/{shopId}.json.
 *
 * <p><b>Lote 2 refactor:</b> this class now consumes the typed
 * {@link RotationShop} hierarchy directly. Schedule decisions are delegated to
 * {@link Scheduler#nextFireTime(long)} — the legacy {@code computeNextFireTime}
 * and {@code isScheduleStale} branches were removed because the {@code Scheduler}
 * abstraction is self-describing (each subtype knows how to compute its own
 * next fire moment, and stale detection collapses to a single "is the persisted
 * timestamp later than what the scheduler would now produce" check).</p>
 */
@Data
public class DataShop {

  private ConcurrentMap<String, ConcurrentMap<String, DynamicRotation>> products = new ConcurrentHashMap<>();

  private static final Path BASE_PATH = CobbleUtils.getPath()
    .resolve(UltraShop.MOD_ID).resolve("data");

  private static final Path ROTATIONS_DIR = BASE_PATH.resolve("rotations");

  private static final Path LEGACY_FILE = BASE_PATH.resolve("dataShop.json");

  /**
   * Tolerance when comparing persisted vs recomputed schedule timestamps.
   */
  private static final long SCHEDULE_DRIFT_TOLERANCE_MS = 60_000L;

  public void init() {
    try {
      migrateFromLegacy();
      loadAllRotations();
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error loading DataShop: " + e.getMessage());
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
        legacy.products.forEach((modId, shopMap) -> shopMap.forEach((shopId, rotation) -> {
          Path file = ROTATIONS_DIR.resolve(modId).resolve(shopId + ".json");
          try {
            Files.createDirectories(file.getParent());
            UtilsFile.write(file, rotation);
          } catch (IOException e) {
            UltraShop.LOGGER.error("Error migrating rotation " + modId + "/" + shopId + ": " + e.getMessage());
          }
        }));
        UltraShop.LOGGER.info("Migrated dataShop.json to per-shop rotation files.");
      }

      // Backup and delete legacy file
      Path backup = LEGACY_FILE.resolveSibling("dataShop.json.bak");
      Files.move(LEGACY_FILE, backup);
      UltraShop.LOGGER.info("Legacy dataShop.json backed up to dataShop.json.bak");
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error during legacy migration: " + e.getMessage());
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
                UltraShop.LOGGER.error("Error loading rotation " + file + ": " + e.getMessage());
              }
            }
          } catch (Exception e) {
            UltraShop.LOGGER.error("Error scanning rotations for " + modId + ": " + e.getMessage());
          }
          if (!shopMap.isEmpty()) {
            products.put(modId, shopMap);
          }
        });
    } catch (IOException e) {
      UltraShop.LOGGER.error("Error scanning rotations directory: " + e.getMessage());
    }
  }

  /**
   * Writes all rotation data to per-shop files.
   */
  public void write() {
    products.forEach((modId, shopMap) ->
      shopMap.forEach((shopId, rotation) -> writeShopRotation(modId, shopId, rotation)));
  }

  /**
   * Writes a single shop's rotation data to disk.
   */
  private void writeShopRotation(String modId, String shopId, DynamicRotation rotation) {
    Path file = ROTATIONS_DIR.resolve(modId).resolve(shopId + ".json");

    try {
      Path parentDir = file.getParent();
      if (parentDir != null && parentDir.toFile().mkdirs()) {
        UltraShop.LOGGER.info("Created rotation directory for mod: " + modId);
      }
      UtilsFile.write(file, rotation);
    } catch (IOException e) {
      UltraShop.LOGGER.error("Error writing rotation " + modId + "/" + shopId + ": ", e);
    }
  }

  /**
   * Updates dynamic products for a {@link RotationShop}, rotating if the cooldown
   * expired or the persisted schedule drifted past the next fire moment.
   *
   * @param shop  rotation shop being inspected
   * @param modId owning mod id (used as rotation namespace on disk)
   * @param force if {@code true}, rotate immediately regardless of schedule
   *
   * @return the (possibly newly rotated) products visible right now
   */
  public List<Product> updateDynamicProducts(RotationShop shop, String modId, boolean force) {
    Scheduler scheduler = shop.getScheduler();
    if (scheduler == null) return shop.activeProducts();


    products.computeIfAbsent(modId, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicRotation());

    if (shop.getProductPool().isEmpty()) return Collections.emptyList();
    
    DynamicRotation rotation = products.get(modId).get(shop.getId());

    if (shop.getProductPool().size() < shop.getRotationAmount()) {
      UltraShop.LOGGER.warn("Rotation shop " + modId + "/" + shop.getId() + " has fewer products in pool than rotation amount. Adjusting rotation amount to " + shop.getProductPool().size());
      shop.setRotationAmount(shop.getProductPool().size());
    }

    synchronized (rotation) {
      long now = System.currentTimeMillis();
      boolean needsUpdate = force
        || rotation.getTimeToUpdate() < now
        || rotation.getProducts().isEmpty()
        || rotation.getProducts().size() != shop.getRotationAmount()
        || isScheduleStale(scheduler, rotation, now);

      if (needsUpdate) {
        ShopContext ctx = ShopContext.get();
        long refreshNow = System.currentTimeMillis();
        rotation.setTimeToUpdate(scheduler.nextFireTime(refreshNow));
        rotation.setProducts(pickWeighted(shop.getProductPool(), shop.getRotationAmount()));

        List<Product> snapshot = List.copyOf(rotation.getProducts());
        ctx.getAsyncContext().runAsync(() -> {
          announceRotationIfEnabled(shop, ctx);
          writeShopRotation(modId, shop.getId(), rotation);
          ctx.getSellIndex().rebuild(ctx.getTypedShops());

          // Send Discord webhook notification
          ShopConfig config = ctx.getConfigs().get(modId);
          String webhookUrl = shop.getWebhookUrl();
          if (webhookUrl == null || webhookUrl.isBlank()) {
            if (config != null && config.getWebhooks() != null) {
              webhookUrl = config.getWebhooks().getRotationWebhookUrl();
            }
          }
          if (webhookUrl != null && !webhookUrl.isBlank()) {
            String shopName = shop.getDisplayConfig() != null && shop.getDisplayConfig().getName() != null
              ? shop.getDisplayConfig().getName() : shop.getId();
            shopName = shopName.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", "");

            StringBuilder prodList = new StringBuilder();
            for (Product p : snapshot) {
              String title = p.getDisplayname();
              if (title == null || title.isBlank()) {
                String finalDisplay = p.getDisplay() != null ? p.getDisplay() : p.getProduct();
                ItemChance itemChance = new ItemChance(finalDisplay, 0);
                String resolvedTitle = itemChance.getTitle();
                if (resolvedTitle != null && !resolvedTitle.isBlank() && !resolvedTitle.startsWith("<lang:")) {
                  title = resolvedTitle;
                } else {
                  title = getCleanNameFromId(p.getProduct());
                }
              }
              title = title.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", "");
              prodList.append("- ").append(title).append("\n");
            }
            String title = "Rotación de Tienda: " + shopName;
            String desc = "La tienda **" + shopName + "** ha rotado su catálogo. Nuevos productos disponibles:\n\n" + prodList.toString();
            String payload = DiscordWebhookHelper.buildEmbedJson(title, desc, 0x00FFFF);
            DiscordWebhookHelper.sendWebhook(webhookUrl, payload);
          }
        });
        return snapshot;
      }
    }

    return rotation.getProducts();
  }

  /**
   * Returns the cooldown expiration timestamp for a shop's rotation. Identified
   * by ids only — DataShop never needs the full shop instance to read state.
   */
  public long getActualCooldown(String modId, String shopId) {
    return products
      .computeIfAbsent(modId, k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shopId, k -> new DynamicRotation())
      .getTimeToUpdate();
  }

  // --- private helpers ---

  /**
   * Stale = the persisted {@code timeToUpdate} is later than what the scheduler
   * would now produce. Catches admin edits to the schedule (e.g. switching from
   * a long interval to a short cron) without forcing a restart.
   */
  private static boolean isScheduleStale(Scheduler scheduler, DynamicRotation rotation, long now) {
    try {
      long expectedNext = scheduler.nextFireTime(now);
      return rotation.getTimeToUpdate() > expectedNext + SCHEDULE_DRIFT_TOLERANCE_MS;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void announceRotationIfEnabled(RotationShop shop, ShopContext ctx) {
    ConditionsConfig cond = shop.getConditionsConfig();
    if (cond == null || !cond.isAnnounceRotation()) return;

    DisplayConfig display = shop.getDisplayConfig();
    String shopName = display != null && display.getName() != null ? display.getName() : shop.getId();

    PlayerUtils.sendMessage(
      (ServerPlayerEntity) null,
      ctx.getLang().getMessageShopRotated().replace("%shop%", shopName),
      ctx.getLang().getPrefix(),
      TypeMessage.BROADCAST
    );
  }

  /**
   * Picks {@code amount} products from {@code pool} using each product's
   * {@link Product#getEffectiveChance() effective chance} as a weight, without
   * replacement. Returns at most {@code min(amount, pool.size())} entries.
   */
  private static List<Product> pickWeighted(List<Product> pool, int amount) {
    List<Product> picked = new ArrayList<>();
    if (pool == null || pool.isEmpty() || amount <= 0) return picked;

    List<Product> available = new ArrayList<>(pool);
    ThreadLocalRandom rand = ThreadLocalRandom.current();
    int target = Math.min(amount, available.size());

    for (int i = 0; i < target && !available.isEmpty(); i++) {
      int totalWeight = available.stream().mapToInt(Product::getEffectiveChance).sum();
      if (totalWeight <= 0) break;
      int r = rand.nextInt(totalWeight);
      int current = 0;
      Product chosen = null;
      for (Product p : available) {
        current += p.getEffectiveChance();
        if (current > r) {
          chosen = p;
          break;
        }
      }
      if (chosen != null) {
        picked.add(chosen);
        available.remove(chosen);
      }
    }
    return picked;
  }

  private static String getCleanNameFromId(String id) {
    if (id == null || id.isBlank()) return "";
    if (id.startsWith("pokemon:")) {
      String rest = id.substring("pokemon:".length()).trim();
      String species = rest.split("\\s+")[0];
      if (!species.isEmpty()) {
        return Character.toUpperCase(species.charAt(0)) + species.substring(1).toLowerCase();
      }
      return "Pokémon";
    }
    if (id.startsWith("command:")) {
      return "Command";
    }
    String path = id;
    int colon = id.indexOf(':');
    if (colon != -1) {
      path = id.substring(colon + 1);
    }
    int brace = path.indexOf('{');
    if (brace != -1) {
      path = path.substring(0, brace);
    }
    String cleaned = path.replace('_', ' ').replace('-', ' ').trim();
    StringBuilder sb = new StringBuilder();
    for (String word : cleaned.split("\\s+")) {
      if (!word.isEmpty()) {
        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
      }
    }
    return sb.toString().trim();
  }
}
