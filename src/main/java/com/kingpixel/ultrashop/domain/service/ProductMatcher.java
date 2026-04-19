package com.kingpixel.ultrashop.domain.service;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;

/**
 * Compares ItemStacks for sell matching — fixes the NBT comparison bug.
 *
 * <p>The old code only compared item type + CustomModelData, ignoring
 * enchantments, custom names, and all other components. This caused
 * items with NBT to sell incorrectly or be discarded.</p>
 */
public final class ProductMatcher {

  private ProductMatcher() {
  }

  /**
   * Full component comparison — items must match in every way.
   * This is the default and correct behavior for selling.
   */
  public static boolean matches(ItemStack playerItem, ItemStack productTemplate) {
    if (playerItem == null || playerItem.isEmpty()) return false;
    if (productTemplate == null || productTemplate.isEmpty()) return false;

    return ItemStack.areItemsAndComponentsEqual(
      withoutCount(playerItem),
      withoutCount(productTemplate)
    );
  }

  /**
   * Creates a copy with count=1 so comparison ignores stack size.
   */
  private static ItemStack withoutCount(ItemStack stack) {
    ItemStack copy = stack.copy();
    copy.setCount(1);
    // Remove lore/name added by the shop display system
    copy.remove(DataComponentTypes.LORE);
    copy.remove(DataComponentTypes.CUSTOM_NAME);
    return copy;
  }
}

