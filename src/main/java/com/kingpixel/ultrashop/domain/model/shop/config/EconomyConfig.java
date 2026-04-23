package com.kingpixel.ultrashop.domain.model.shop.config;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Immutable snapshot of a Shop's economy configuration.
 *
 * <p>Groups all currency / pricing / discount fields into a single value object.
 * Field semantics are preserved one-to-one from legacy {@code Shop}:</p>
 * <ul>
 *   <li>{@link #economies} — Ordered set of accepted currencies. The first entry
 *       is the "primary" economy (returned by {@code Shop.getPrimaryEconomy()}).</li>
 *   <li>{@link #globalDiscount} — Default discount percentage applied to all products
 *       (overridden per-permission by {@link #discounts}).</li>
 *   <li>{@link #discounts} — Per-permission discount map (e.g. {@code "group.vip" -> 2.0}).</li>
 * </ul>
 *
 * <p><b>Backwards compatibility:</b> read-through snapshot in Phase 1 — the JSON
 * shape of {@code Shop} stays the same. Existing config files load unchanged.</p>
 */
@Value
@Builder(toBuilder = true)
public class EconomyConfig {

    /**
     * Ordered set of accepted currencies. Must contain at least one entry — when
     * empty, {@code Shop.check()} fills in a default Impactor dollars entry.
     */
    LinkedHashSet<EconomyUse> economies;

    float globalDiscount;

    /** Permission-keyed discounts. Never null in canonical state — defaults to empty. */
    Map<String, Float> discounts;
}



