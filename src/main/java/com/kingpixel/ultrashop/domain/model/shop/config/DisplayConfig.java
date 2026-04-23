package com.kingpixel.ultrashop.domain.model.shop.config;

import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable snapshot of a Shop's visual / layout configuration.
 *
 * <p>This Value Object groups visual fields that previously lived directly on
 * {@code Shop} (Phase 1 of the refactor). It exists to:
 * <ul>
 *   <li>Make the Shop class smaller and obey Single Responsibility Principle.</li>
 *   <li>Allow visual config to be reused / passed around independently of the Shop.</li>
 *   <li>Pave the way for Phase 3 where each {@code ShopType} composes its own VOs.</li>
 * </ul>
 *
 * <p><b>Backwards compatibility:</b> in Phase 1, this VO is built read-through
 * from {@code Shop} fields. The on-disk JSON shape of {@code Shop} remains
 * unchanged. Existing config files load without migration.</p>
 *
 * <p>Field semantics match the legacy {@code Shop} fields one-to-one:</p>
 * <ul>
 *   <li>{@link #name} — Display name of the shop (used in titles / placeholders).</li>
 *   <li>{@link #title} — Menu title template (supports {@code %shop%}).</li>
 *   <li>{@link #autoPlace} — Whether products auto-place inside {@link #rectangle}.</li>
 *   <li>{@link #rows} — Number of inventory rows for the shop GUI.</li>
 *   <li>{@link #colorProduct} — Color tag applied to product names (legacy formatting).</li>
 *   <li>{@link #rectangle} — Region of the GUI where products are auto-placed.</li>
 *   <li>{@link #displayItem} — Icon item representing this shop (e.g. in category menus).</li>
 *   <li>{@link #itemInfoShop}, {@link #itemBalance}, {@link #itemPrevious},
 *       {@link #itemClose}, {@link #itemNext} — Standard menu chrome items.</li>
 *   <li>{@link #panels} — Background panels (filler items) for the GUI.</li>
 * </ul>
 */
@Value
@Builder(toBuilder = true)
public class DisplayConfig {

    String name;

    @Nullable
    String title;

    boolean autoPlace;

    int rows;

    @Nullable
    String colorProduct;

    @Nullable
    Rectangle rectangle;

    /** Icon representing the shop itself (the "display" field on legacy Shop). */
    @Nullable
    ItemModel displayItem;

    @Nullable ItemModel itemInfoShop;
    @Nullable ItemModel itemBalance;
    @Nullable ItemModel itemPrevious;
    @Nullable ItemModel itemClose;
    @Nullable ItemModel itemNext;

    @Nullable
    List<PanelsConfig> panels;
}


