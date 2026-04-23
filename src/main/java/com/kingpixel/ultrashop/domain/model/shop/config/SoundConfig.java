package com.kingpixel.ultrashop.domain.model.shop.config;

import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a Shop's sound configuration.
 *
 * <ul>
 *   <li>{@link #soundOpen} — Sound event ID played when a player opens the shop
 *       (e.g. {@code "minecraft:block.chest.open"}).</li>
 *   <li>{@link #soundClose} — Sound event ID played when the shop is closed
 *       (e.g. {@code "minecraft:block.chest.close"}).</li>
 * </ul>
 *
 * <p>Both fields are nullable to mean "play nothing" — preserving legacy behavior
 * where empty/null sound IDs disable the cue.</p>
 *
 * <p><b>Backwards compatibility:</b> read-through snapshot in Phase 1.</p>
 */
@Value
@Builder(toBuilder = true)
public class SoundConfig {

    @Nullable
    String soundOpen;

    @Nullable
    String soundClose;
}

