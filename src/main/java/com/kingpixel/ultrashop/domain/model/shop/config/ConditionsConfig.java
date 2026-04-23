package com.kingpixel.ultrashop.domain.model.shop.config;

import com.kingpixel.cobbleutils.Model.conditions.Condition;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable snapshot of a Shop's behavior / accessibility configuration.
 *
 * <p>Bundles fields that determine WHEN the shop is usable and WHAT happens around
 * its lifecycle, separately from visual ({@link DisplayConfig}) and economic
 * ({@link EconomyConfig}) concerns.</p>
 *
 * <ul>
 *   <li>{@link #openConditions} — Predicates evaluated on shop open. If any fails,
 *       the shop refuses to open. Never null in canonical state — defaults to empty list.</li>
 *   <li>{@link #closeCommand} — Optional command executed when the player closes the shop.</li>
 *   <li>{@link #announceRotation} — When {@code true}, broadcasts a server-wide message
 *       each time a rotation shop refreshes its catalog.</li>
 * </ul>
 *
 * <p><b>Backwards compatibility:</b> read-through snapshot in Phase 1.</p>
 */
@Value
@Builder(toBuilder = true)
public class ConditionsConfig {

    List<Condition> openConditions;

    @Nullable
    String closeCommand;

    boolean announceRotation;
}

