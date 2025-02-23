package com.kingpixel.cobbleshop.config;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.gui.MenuBuyAndSell;
import com.kingpixel.cobbleshop.models.InfoShopType;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Lang {
  private String prefix;
  private String messageShopNotOpen;
  private String messageNotHavePermission;
  private String messageNotEnoughMoney;
  private String messageNotSell;
  private String messageSell;
  private String formatSell;
  private InfoShopType infoShopType;
  private List<String> infoProduct;
  // Buttons Shop
  private ItemModel globalDisplay;
  private ItemModel globalItemInfoShop;
  private ItemModel globalItemBalance;
  // Button add
  private ItemModel add1;
  private ItemModel add8;
  private ItemModel add16;
  private ItemModel add64;
  // Button remove
  private ItemModel remove1;
  private ItemModel remove8;
  private ItemModel remove16;
  private ItemModel remove64;


  // Button Pages
  private ItemModel globalItemPrevious;
  private ItemModel globalItemClose;
  private ItemModel globalItemNext;
  private MenuBuyAndSell menuBuyAndSell;


  public Lang() {
    prefix = "§7[§6CobbleShop§7] ";
    messageShopNotOpen = "%prefix% §cThe shop is not open";
    messageNotHavePermission = "%prefix% §cYou do not have permission to use this command";
    messageNotEnoughMoney = "%prefix% §cYou do not have enough money";
    messageNotSell = "%prefix% §cYou don't have anything to sell";
    messageSell = "%prefix% §aYou have sold:\n %sell%";
    formatSell = " &7- %price%";
    infoShopType = new InfoShopType();
    infoProduct = List.of(
      "%info%",
      " ",
      "&7Pack: %pack%",
      "&7Amount: %amount%",
      " ",
      "&7Buy: &a%buy% %discount% %removebuy%",
      "&7Sell: &c%sell% %removesell%",
      " ",
      "&7Left click to buy %removebuy%",
      "&7Right click to sell %removesell%"
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
    add1 = new ItemModel(21, "item:1:minecraft:lime_stained_glass_pane", "&aAdd 1", List.of(""), 0);
    add8 = new ItemModel(20, "item:8:minecraft:lime_stained_glass_pane", "&aAdd 8", List.of(""), 0);
    add16 = new ItemModel(20, "item:16:minecraft:lime_stained_glass_pane", "&aAdd 16", List.of(""), 0);
    add64 = new ItemModel(19, "item:64:minecraft:lime_stained_glass_pane", "&aAdd 64", List.of(""), 0);
    remove1 = new ItemModel(23, "item:1:minecraft:red_stained_glass_pane", "&cRemove 1", List.of(""), 0);
    remove8 = new ItemModel(24, "item:8:minecraft:red_stained_glass_pane", "&cRemove 8", List.of(""), 0);
    remove16 = new ItemModel(24, "item:16:minecraft:red_stained_glass_pane", "&cRemove 16", List.of(""), 0);
    remove64 = new ItemModel(25, "item:64:minecraft:red_stained_glass_pane", "&cRemove 64", List.of(""), 0);
    menuBuyAndSell = new MenuBuyAndSell();
  }

  public void init(Config config) {
    File folder = Utils.getAbsolutePath(CobbleShop.PATH_LANG);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(CobbleShop.PATH_LANG, config.getLang() + ".json",
      call -> {
        CobbleShop.lang = CobbleShop.gson.fromJson(call, Lang.class);
        CobbleShop.lang.check();
        CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleShop.PATH_LANG, config.getLang() + ".json",
          CobbleShop.gson.toJson(CobbleShop.lang));
        if (!futureWrite.join()) {
          CobbleUtils.LOGGER.error("Error writing file: " + CobbleShop.PATH_LANG + config.getLang() + ".json");
        }
      });

    if (!futureRead.join()) {
      CobbleShop.lang = this;
      CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleShop.PATH_LANG, config.getLang() + ".json",
        CobbleShop.gson.toJson(CobbleShop.lang));
      if (!futureWrite.join()) {
        CobbleUtils.LOGGER.error("Error writing file: " + CobbleShop.PATH_LANG + config.getLang() + ".json");
      }
    }
  }

  private void check() {
  }

  public ItemModel getGlobalDisplay(ItemModel item) {
    if (item == null) return globalDisplay;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalDisplay;
    return item;
  }

  public ItemModel getGlobalItemInfoShop(ItemModel item) {
    if (item == null) return globalItemInfoShop;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalItemInfoShop;
    return item;
  }

  public ItemModel getGlobalItemBalance(ItemModel item) {
    if (item == null) return globalItemBalance;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalItemBalance;
    return item;
  }

  public ItemModel getGlobalItemPrevious(ItemModel item) {
    if (item == null) return globalItemPrevious;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalItemPrevious;
    return item;
  }

  public ItemModel getGlobalItemClose(ItemModel item) {
    if (item == null) return globalItemClose;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalItemClose;
    return item;
  }

  public ItemModel getGlobalItemNext(ItemModel item) {
    if (item == null) return globalItemNext;
    String itemS = item.getItem();
    if (itemS == null || itemS.isEmpty()) return globalItemNext;
    return item;
  }

}
