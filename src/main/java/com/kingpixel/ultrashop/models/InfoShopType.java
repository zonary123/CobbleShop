package com.kingpixel.ultrashop.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
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
  private ItemModel shopCalendar;

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
    this.shopCalendar = new ItemModel(4, "minecraft:book", "Info Shop", List.of(
      "§7This is a calendar shop,",
      "§7this shop is only open",
      "§7on certain range of date."
    ), 0);
  }

  public GooeyButton getShopType(Shop shop, ShopOptionsApi options, ItemModel supportItemModel) {
    ItemModel itemModel;
    String title;
    List<String> lore;
    itemModel = switch (shop.getType().getTypeShop()) {
      case PERMANENT -> shopPermanent;
      case DYNAMIC -> shopDynamic;
      case WEEKLY -> shopWeekly;
      case DYNAMIC_WEEKLY -> shopDynamicWeekly;
      case CALENDAR -> shopCalendar;
      case DYNAMIC_CALENDAR -> shopCalendar;
      default -> throw new IllegalStateException("Unexpected value: " + shop.getType().getTypeShop());
    };

    if (supportItemModel.getDisplayname().isEmpty()) {
      title = itemModel.getDisplayname();
    } else {
      title = supportItemModel.getDisplayname();
    }

    if (supportItemModel.getLore().isEmpty()) {
      lore = new ArrayList<>(itemModel.getLore());
    } else {
      lore = new ArrayList<>(supportItemModel.getLore());
    }

    if (!supportItemModel.getItem().isEmpty()) {
      itemModel = supportItemModel;
    }

    lore.replaceAll(s -> shop.getType().replace(s, shop, options));
    return itemModel.getButton(1,
      title,
      lore,
      action -> {

      });
  }


}
