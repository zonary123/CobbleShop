package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.cobbleutils.Model.EconomyUse;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Minimal capability surface that BOTH the legacy
 * {@link com.kingpixel.ultrashop.domain.model.Shop} class and the new sealed
 * {@link Shop} hierarchy expose.
 *
 * <p>Exists so that {@code Product}, {@code PriceCalculator} and other downstream
 * services can accept either representation without forcing a coordinated
 * mass-migration of every callsite. This is the {@code Adapter Pattern}
 * applied at the type-system level.</p>
 *
 * <p>The four methods captured here are the EXACT subset of legacy {@code Shop}
 * methods that pricing / discount / placement logic actually depends on.
 * Identified by static analysis — see grep for {@code shop.\\.} in
 * {@code Product} and {@code PriceCalculator}.</p>
 *
 * <p>Phase 4 cleanup deletes this interface and folds the capabilities into
 * {@link Shop} directly, once the legacy class is removed.</p>
 */
public interface ShopReference {

  /** Stable identifier for the shop (file name without {@code .json}). */
  String getId();

  /** Default currencies accepted by this shop, in declaration order. */
  LinkedHashSet<EconomyUse> getEconomies();

  /** Whether products without an explicit slot are auto-arranged. */
  boolean isAutoPlace();

  /** Shop-wide discount percentage applied to all products. */
  float getGlobalDiscount();

  /** Permission-keyed discount overrides ({@code "group.vip" -> 2.0f}). */
  Map<String, Float> getDiscounts();
}

