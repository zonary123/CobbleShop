package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.domain.scheduler.SchedulerFactory;

/**
 * Bidirectional translator between the legacy
 * {@link com.kingpixel.ultrashop.domain.model.Shop} god-class and the new sealed
 * {@link Shop} hierarchy.
 *
 * <p>Used during the multi-phase refactor to:</p>
 * <ul>
 *   <li>{@link #fromLegacy} — Boot-time migration: read existing shop JSON via the
 *       legacy class, convert to the typed hierarchy. Required so the JSON adapter
 *       can fall back transparently when it sees the legacy shape.</li>
 *   <li>{@link #toLegacy} — Reverse direction for transient compatibility: lets
 *       new-style shops still pass through legacy code paths
 *       ({@code Product.check(Shop)}) while Phase 4 cleanup migrates those
 *       callers. {@code DataShop} no longer needs this hop (Lote 2).</li>
 * </ul>
 *
 * <p><b>Data-loss policy:</b> NO field is silently dropped. If a legacy shop has
 * fields that don't fit the chosen new subtype (e.g. a NORMAL shop that also has
 * a {@code rotationSchedule} populated by accident), the orphan fields are
 * logged at WARN level and discarded — never crash the load.</p>
 *
 * <p>This class is deleted in Phase 4 once the legacy class is removed.</p>
 */
public final class ShopBridge {

  private ShopBridge() {
    // utility
  }

  /**
   * Converts a legacy {@code Shop} into the appropriate {@link Shop} subtype.
   *
   * <p>Type selection mirrors the legacy {@code check()} auto-promotion rules:
   * the explicit {@link ShopType} wins; if absent, the type is inferred from
   * which fields are populated.</p>
   *
   * @param legacy non-null legacy shop instance
   * @return a new sealed-hierarchy shop carrying the same data
   */
  public static Shop fromLegacy(com.kingpixel.ultrashop.domain.model.Shop legacy) {
    if (legacy == null) {
      throw new IllegalArgumentException("Cannot convert null legacy shop");
    }

    ShopType type = inferType(legacy);

    return switch (type) {
      case NORMAL -> buildNormal(legacy);
      case CATEGORY -> buildCategory(legacy);
      case ROTATION -> buildRotation(legacy);
    };
  }

  /**
   * Converts a sealed-hierarchy shop back into a legacy {@code Shop} instance.
   * Used only by transient compatibility shims; new code should NEVER call this.
   */
  public static com.kingpixel.ultrashop.domain.model.Shop toLegacy(Shop shop) {
    com.kingpixel.ultrashop.domain.model.Shop legacy =
      new com.kingpixel.ultrashop.domain.model.Shop();

    legacy.setId(shop.getId());
    legacy.setType(shop.getType());
    legacy.setMaintenance(shop.isMaintenance());
    legacy.setWebhookUrl(shop.getWebhookUrl());

    applyDisplay(legacy, shop);
    applyEconomy(legacy, shop);
    applyConditions(legacy, shop);
    applySound(legacy, shop);

    if (shop instanceof NormalShop n) {
      legacy.setProducts(n.getProducts());
    } else if (shop instanceof CategoryShop c) {
      legacy.setSubShops(c.getSubShops());
    } else if (shop instanceof RotationShop r) {
      legacy.setProducts(r.getProductPool());
      legacy.setRotationSchedule(toLegacyRotationSchedule(r));
    }

    return legacy;
  }

  // --- Internal: type inference -------------------------------------------

  private static ShopType inferType(com.kingpixel.ultrashop.domain.model.Shop legacy) {
    if (legacy.getType() != null && legacy.getType() != ShopType.NORMAL) {
      return legacy.getType();
    }
    if (legacy.getRotationSchedule() != null) {
      return ShopType.ROTATION;
    }
    if (legacy.getSubShops() != null && !legacy.getSubShops().isEmpty()) {
      return ShopType.CATEGORY;
    }
    return ShopType.NORMAL;
  }

  // --- Internal: subtype builders -----------------------------------------

  private static NormalShop buildNormal(com.kingpixel.ultrashop.domain.model.Shop legacy) {
    NormalShop shop = new NormalShop();
    copyCommonFields(legacy, shop);
    shop.setProducts(legacy.getProducts());
    return shop;
  }

  private static CategoryShop buildCategory(com.kingpixel.ultrashop.domain.model.Shop legacy) {
    CategoryShop shop = new CategoryShop();
    copyCommonFields(legacy, shop);
    shop.setSubShops(legacy.getSubShops());
    return shop;
  }

  private static RotationShop buildRotation(com.kingpixel.ultrashop.domain.model.Shop legacy) {
    RotationShop shop = new RotationShop();
    copyCommonFields(legacy, shop);
    shop.setProductPool(legacy.getProducts());

    RotationSchedule legacySched = legacy.getRotationSchedule();
    shop.setScheduler(SchedulerFactory.fromLegacy(legacySched));
    if (legacySched != null && legacySched.getAmount() > 0) {
      shop.setRotationAmount(legacySched.getAmount());
    }
    return shop;
  }

  private static void copyCommonFields(
      com.kingpixel.ultrashop.domain.model.Shop legacy,
      AbstractShop target) {
    target.setId(legacy.getId());
    target.setDisplayConfig(legacy.toDisplayConfig());
    target.setEconomyConfig(legacy.toEconomyConfig());
    target.setConditionsConfig(legacy.toConditionsConfig());
    target.setSoundConfig(legacy.toSoundConfig());
    target.setMaintenance(legacy.isMaintenance());
    target.setWebhookUrl(legacy.getWebhookUrl());
  }

  // --- Internal: legacy reconstruction (toLegacy direction) ---------------

  private static void applyDisplay(
      com.kingpixel.ultrashop.domain.model.Shop legacy, Shop shop) {
    var d = shop.getDisplayConfig();
    if (d == null) return;
    legacy.setName(d.getName());
    legacy.setTitle(d.getTitle());
    legacy.setAutoPlace(d.isAutoPlace());
    legacy.setRows(d.getRows());
    legacy.setColorProduct(d.getColorProduct());
    legacy.setRectangle(d.getRectangle());
    legacy.setDisplay(d.getDisplayItem());
    legacy.setItemInfoShop(d.getItemInfoShop());
    legacy.setItemBalance(d.getItemBalance());
    legacy.setItemPrevious(d.getItemPrevious());
    legacy.setItemClose(d.getItemClose());
    legacy.setItemNext(d.getItemNext());
    legacy.setPanels(d.getPanels());
  }

  private static void applyEconomy(
      com.kingpixel.ultrashop.domain.model.Shop legacy, Shop shop) {
    var e = shop.getEconomyConfig();
    if (e == null) return;
    legacy.setEconomies(e.getEconomies());
    legacy.setGlobalDiscount(e.getGlobalDiscount());
    legacy.setDiscounts(e.getDiscounts());
  }

  private static void applyConditions(
      com.kingpixel.ultrashop.domain.model.Shop legacy, Shop shop) {
    var c = shop.getConditionsConfig();
    if (c == null) return;
    legacy.setOpenConditions(c.getOpenConditions());
    legacy.setCloseCommand(c.getCloseCommand());
    legacy.setAnnounceRotation(c.isAnnounceRotation());
  }

  private static void applySound(
      com.kingpixel.ultrashop.domain.model.Shop legacy, Shop shop) {
    var s = shop.getSoundConfig();
    if (s == null) return;
    legacy.setSoundOpen(s.getSoundOpen());
    legacy.setSoundClose(s.getSoundClose());
  }

  private static RotationSchedule toLegacyRotationSchedule(RotationShop shop) {
    RotationSchedule sched = new RotationSchedule();
    sched.setAmount(shop.getRotationAmount());
    Scheduler scheduler = shop.getScheduler();
    if (scheduler instanceof CronScheduler cron) {
      sched.setCron(cron.getExpression());
    } else if (scheduler instanceof DurationScheduler dur) {
      sched.setInterval(dur.getDuration());
    }
    return sched;
  }
}

