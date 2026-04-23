package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.scheduler.Scheduler;

/**
 * Promotes / demotes shops between subtypes preserving the four config Value
 * Objects ({@code DisplayConfig}, {@code EconomyConfig}, {@code ConditionsConfig},
 * {@code SoundConfig}) and the id.
 *
 * <p>Used by the admin editor when the user toggles "rotation enabled" on a
 * shop — the type changes but the rest of the config must survive intact.</p>
 */
public final class ShopTypeConverter {

  private ShopTypeConverter() {
    // utility
  }

  /**
   * Converts a {@link NormalShop} into a {@link RotationShop}, moving its
   * declared products into the rotation pool.
   *
   * @param source    non-null shop being promoted
   * @param scheduler scheduler driving the rotation
   * @param amount    products picked per rotation tick (clamped to {@code >= 1})
   * @return a new {@link RotationShop} carrying the same id and config VOs
   */
  public static RotationShop promoteToRotation(NormalShop source, Scheduler scheduler, int amount) {
    RotationShop rotation = new RotationShop();
    copyCommon(source, rotation);
    rotation.setProductPool(source.getProducts());
    rotation.setScheduler(scheduler);
    rotation.setRotationAmount(Math.max(1, amount));
    return rotation;
  }

  /**
   * Converts a {@link RotationShop} back into a {@link NormalShop}, exposing its
   * full {@code productPool} as the static catalog.
   *
   * @param source non-null shop being demoted
   * @return a new {@link NormalShop} carrying the same id and config VOs
   */
  public static NormalShop demoteToNormal(RotationShop source) {
    NormalShop normal = new NormalShop();
    copyCommon(source, normal);
    normal.setProducts(source.getProductPool());
    return normal;
  }

  private static void copyCommon(AbstractShop from, AbstractShop to) {
    to.setId(from.getId());
    to.setDisplayConfig(from.getDisplayConfig());
    to.setEconomyConfig(from.getEconomyConfig());
    to.setConditionsConfig(from.getConditionsConfig());
    to.setSoundConfig(from.getSoundConfig());
  }
}

