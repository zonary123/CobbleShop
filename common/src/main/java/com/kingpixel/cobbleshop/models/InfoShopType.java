package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleutils.Model.ItemModel;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:09
 */
@Data
public class InfoShopType {
  private ItemModel shopPermanent;
  private ItemModel shopDynamic;
  private ItemModel shopWeekly;
  private ItemModel shopDynamicWeekly;

  public InfoShopType() {
    this.shopPermanent = new ItemModel(0, "minecraft:book", "Info Shop", List.of(
      "§7This is a permanent shop,",
      "§7you can buy items here",
      "§7at any time."
    ), 0);
    this.shopDynamic = new ItemModel(1, "minecraft:book", "Info Shop", List.of(
      "§7This is a dynamic shop,",
      "§7the items change every",
      "§7certain time.",
      "",
      "&7Cooldown to change: %cooldown%",
      "&7Number items: %number%"
    ), 0);
    this.shopWeekly = new ItemModel(2, "minecraft:book", "Info Shop", List.of(
      "§7This is a weekly shop,",
      "&7you can only join once a",
      "§7week.",
      "",
      "&7Days: %days%"
    ), 0);
    this.shopDynamicWeekly = new ItemModel(3, "minecraft:book", "Info Shop", List.of(
      "§7This is a dynamic weekly shop,",
      "§7the items change every",
      "§7certain time and every week.",
      "",
      "&7Cooldown to change: %cooldown%",
      "&7Number items: %number%",
      "&7Days: %days%"
    ), 0);
  }

  public GooeyButton getShopType(Shop shop, ShopOptionsApi options) {
    ItemModel itemModel;
    List<String> lore;
    itemModel = switch (shop.getType().getTypeShop()) {
      case TypeShop.PERMANENT -> shopPermanent;
      case TypeShop.DYNAMIC -> shopDynamic;
      case TypeShop.WEEKLY -> shopWeekly;
      case TypeShop.DYNAMIC_WEEKLY -> shopDynamicWeekly;
    };
    lore = new ArrayList<>(itemModel.getLore());
    lore.replaceAll(s -> shop.getType().replace(s, shop, options));
    return itemModel.getButton(0,
      null,
      lore,
      action -> {

      });
  }


}
