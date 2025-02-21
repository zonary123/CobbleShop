package com.kingpixel.cobbleshop.config;

import com.kingpixel.cobbleshop.gui.MenuBuyAndSell;
import com.kingpixel.cobbleshop.models.InfoShopType;
import com.kingpixel.cobbleutils.Model.ItemModel;
import lombok.Data;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Lang {
  private String prefix;
  private InfoShopType infoShopType;
  private List<String> infoProduct;
  // Buttons Shop
  private ItemModel globalDisplay;
  private ItemModel globalItemInfoShop;
  private ItemModel globalItemBalance;
  // Button add
  private ItemModel add1;
  private ItemModel add8;
  private ItemModel add10;
  private ItemModel add16;
  private ItemModel add64;
  // Button remove
  private ItemModel remove1;
  private ItemModel remove8;
  private ItemModel remove10;
  private ItemModel remove16;
  private ItemModel remove64;

  // Button Pages
  private ItemModel globalItemPrevious;
  private ItemModel globalItemClose;
  private ItemModel globalItemNext;
  private MenuBuyAndSell menuBuyAndSell;


  public Lang() {
    prefix = "§7[§6CobbleShop§7] ";
    infoShopType = new InfoShopType();
    infoProduct = List.of(
      "",
      "&7Amount: %amount%x%amountproduct%=%total%",
      "&7Buy: &a%buy% %discount% %removebuy%",
      "&7Sell: &c%sell% %removesell%",
      "",
      "&7Left click to buy %removebuy%",
      "&7Right click to sell %removesell%",
      "",
      "&7Balance: &e%balance%",
      ""
    );
    globalDisplay = new ItemModel(0, "cobblemon:poke_ball", "&aShop %shop%", List.of(""), 0);
    globalItemInfoShop = new ItemModel(0, "minecraft:book", "&aInfo", List.of(
      "%info%"
    ), 0);
    globalItemBalance = new ItemModel(0, "minecraft:emerald", "&aBalance", List.of(
      "&7You have %amount%"
    ), 0);
    globalItemPrevious = new ItemModel(0, "minecraft:arrow", "&aPrevious", List.of(""), 0);
    globalItemClose = new ItemModel(0, "minecraft:barrier", "&cClose", List.of(""), 0);
    globalItemNext = new ItemModel(0, "minecraft:arrow", "&aNext", List.of(""), 0);
    add1 = new ItemModel(0, "item:1:minecraft:lime_stained_glass_pane", "&aAdd 1", List.of(""), 0);
    add8 = new ItemModel(0, "item:8:minecraft:lime_stained_glass_pane", "&aAdd 8", List.of(""), 0);
    add10 = new ItemModel(0, "item:10:minecraft:lime_stained_glass_pane", "&aAdd 10", List.of(""), 0);
    add16 = new ItemModel(0, "item:16:minecraft:lime_stained_glass_pane", "&aAdd 16", List.of(""), 0);
    add64 = new ItemModel(0, "item:64:minecraft:lime_stained_glass_pane", "&aAdd 64", List.of(""), 0);
    remove1 = new ItemModel(0, "item:1:minecraft:red_stained_glass_pane", "&cRemove 1", List.of(""), 0);
    remove8 = new ItemModel(0, "item:8:minecraft:red_stained_glass_pane", "&cRemove 8", List.of(""), 0);
    remove10 = new ItemModel(0, "item:10:minecraft:red_stained_glass_pane", "&cRemove 10", List.of(""), 0);
    remove16 = new ItemModel(0, "item:16:minecraft:red_stained_glass_pane", "&cRemove 16", List.of(""), 0);
    remove64 = new ItemModel(0, "item:64:minecraft:red_stained_glass_pane", "&cRemove 64", List.of(""), 0);
  }

  public void init() {
  }

  public ItemModel getGlobalDisplay(ItemModel item) {
    if (item.getItem().isEmpty()) return globalDisplay;
    return item;
  }

  public ItemModel getGlobalItemInfoShop(ItemModel item) {
    if (item.getItem().isEmpty()) return globalItemInfoShop;
    return item;
  }

  public ItemModel getGlobalItemBalance(ItemModel item) {
    if (item.getItem().isEmpty()) return globalItemBalance;
    return item;
  }

  public ItemModel getGlobalItemPrevious(ItemModel item) {
    if (item.getItem().isEmpty()) return globalItemPrevious;
    return item;
  }

  public ItemModel getGlobalItemClose(ItemModel item) {
    if (item.getItem().isEmpty()) return globalItemClose;
    return item;
  }

  public ItemModel getGlobalItemNext(ItemModel item) {
    if (item.getItem().isEmpty()) return globalItemNext;
    return item;
  }

}
