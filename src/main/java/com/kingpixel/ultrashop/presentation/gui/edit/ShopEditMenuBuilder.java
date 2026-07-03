package com.kingpixel.ultrashop.presentation.gui.edit;

import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class ShopEditMenuBuilder {

  private ShopEditMenuBuilder() {
  }

  public static void openShopList(ServerPlayerEntity player, ShopConfig config, String modId) {
    ShopListEditor.openShopList(player, config, modId);
  }

  public static void openProductList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ProductListEditor.openProductList(player, shop, config, modId);
  }

  public static void openProductEditor(ServerPlayerEntity player, Shop shop, Product product,
                                       ShopConfig config, String modId) {
    ProductEditor.openProductEditor(player, shop, product, config, modId);
  }

  public static void openShopSettings(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopSettingsEditor.openShopSettings(player, shop, config, modId);
  }

  public static void openInventoryPicker(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ProductListEditor.openInventoryPicker(player, shop, config, modId);
  }

  public static void openInventoryPickerForEdit(ServerPlayerEntity player, Shop shop, Product product,
                                                ShopConfig config, String modId) {
    ProductEditor.openInventoryPickerForEdit(player, shop, product, config, modId);
  }

  public static void openConditionsList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopSettingsEditor.openConditionsList(player, shop, config, modId);
  }

  public static void openConditionTypeSelector(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopSettingsEditor.openConditionTypeSelector(player, shop, config, modId);
  }

  public static void openProductConditionsList(ServerPlayerEntity player, Shop shop, Product product,
                                               ShopConfig config, String modId, boolean isVisibility) {
    ProductEditor.openProductConditionsList(player, shop, product, config, modId, isVisibility);
  }

  public static void openProductConditionTypeSelector(ServerPlayerEntity player, Shop shop, Product product,
                                                      ShopConfig config, String modId, boolean isVisibility) {
    ProductEditor.openProductConditionTypeSelector(player, shop, product, config, modId, isVisibility);
  }

  public static void openPokemonAddOptions(ServerPlayerEntity player, Shop shop, List<Product> products,
                                           ShopConfig config, String modId) {
    ProductListEditor.openPokemonAddOptions(player, shop, products, config, modId);
  }

  public static List<Product> getEditableProducts(Shop shop) {
    return ProductListEditor.getEditableProducts(shop);
  }
}
