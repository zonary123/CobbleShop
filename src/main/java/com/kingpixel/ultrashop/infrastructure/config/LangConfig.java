package com.kingpixel.ultrashop.infrastructure.config;

import com.kingpixel.cobbleutils.Model.ItemModel;
import lombok.Data;

import java.util.List;

/**
 * POJO for language/message configuration.
 * No GUI instances, no I/O — pure data.
 */
@Data
public class LangConfig {
  // --- Messages ---
  private String prefix;
  private String messageNotBuyPermission;
  private String messageNotHavePermission;
  private String messageShopNotOpen;
  private String messageNotEnoughMoney;
  private String messageNotSell;
  private String messageSell;
  private String messageSimpleSell;
  private String messageYouCantBuyNow;
  private String messageBuyPriceLessThanSell;
  private String messageNotEnoughSpace;
  private String formatSell;
  private String notExtraInfo;
  private String messageShopRotated;

  // --- Product lore template ---
  private List<String> infoProduct;

  // --- Shop info display items ---
  private ItemModel shopInfoPermanent;
  private ItemModel shopInfoDynamic;

  // --- Global display fallbacks ---
  private ItemModel globalDisplay;
  private ItemModel globalItemInfoShop;
  private ItemModel globalItemBalance;
  private ItemModel globalItemPrevious;
  private ItemModel globalItemClose;
  private ItemModel globalItemNext;

  // --- Buy/sell quantity buttons ---
  private ItemModel add1;
  private ItemModel add8;
  private ItemModel add16;
  private ItemModel add64;
  private ItemModel remove1;
  private ItemModel remove8;
  private ItemModel remove16;
  private ItemModel remove64;

  // --- Buy/sell menu config ---
  private BuyAndSellConfig menuBuyAndSell;

  public LangConfig() {
    prefix = "<#4ddb93>[<#ebb35a>UltraShop🏪<#4ddb93>] ";
    messageShopNotOpen = "%prefix% <#eb4747>The shop is not open";
    messageShopRotated = "%prefix% <#4ddb93>The shop %shop% has been rotated";
    messageNotBuyPermission = "%prefix% <#eb4747>You can't buy this product";
    messageNotHavePermission = "%prefix% <#eb4747>You do not have the permission for this shop -> %permission%";
    messageNotEnoughMoney = "%prefix% <#eb4747>You do not have enough money";
    messageNotSell = "%prefix% <#eb4747>You don't have anything to sell";
    messageSell = "%prefix% <#4ddb93>You have sold:\n %sell%";
    messageNotEnoughSpace = "%prefix% <#eb4747>You don't have enough space in your inventory";
    messageBuyPriceLessThanSell = "%prefix% <#eb4747>The buy price is less than the sell price";
    messageSimpleSell = "%prefix% <#4ddb93>You have sold %amount% and you have earned %price%";
    messageYouCantBuyNow = "%prefix% <#eb4747>You can't buy now the product you get the limit -> %limit%. You have to wait %time% seconds";
    formatSell = " <#bfbfbf>- <#f1d46B>%price%";
    notExtraInfo = "<#bfbfbf>No extra information";

    infoProduct = List.of(
      "%info%",
      " ",
      "<#bfbfbf>Pack: <#4da6ff>%pack%",
      "<#bfbfbf>Amount: <#f4d03f>%amount%",
      " ",
      "<#bfbfbf>Buy: <#4ddb93>%buy% %removebuy%",
      "<#bfbfbf>Discount: <#ffac33>%discount% %removediscount%",
      "<#bfbfbf>Sell: <#eb4747>%sell% %removesell%",
      " ",
      "<#bfbfbf>Stock: <#4da6ff>%remaining%<#bfbfbf>/<#f4d03f>%limit% %removelimit%",
      "<#bfbfbf>Cooldown: <#ffac33>%cooldown_time% %removelimit%",
      " ",
      "<#bfbfbf>Left click to buy %removebuy%",
      "<#bfbfbf>Right click to sell %removesell%",
      " ",
      "<#bfbfbf>Your Balance: %balance%"
    );

    shopInfoPermanent = new ItemModel(0, "minecraft:book", "Info Shop", List.of(
      "§7This is a permanent shop,",
      "§7you can buy items here",
      "§7at any time."
    ), 0);
    shopInfoDynamic = new ItemModel(1, "minecraft:clock", "Info Shop", List.of(
      "§7This is a dynamic shop,",
      "§7the items rotate periodically.",
      "",
      "§7Next rotation: §e%cooldown%",
      "§7Showing: §a%number% §7/ §f%totalProducts% §7products"
    ), 0);

    globalDisplay = new ItemModel(0, "cobblemon:poke_ball", "<#4ddb93>Shop %shop%", List.of(""), 0);
    globalItemInfoShop = new ItemModel(0, "minecraft:book", "<#4ddb93>Info", List.of("%info%"), 0);
    globalItemBalance = new ItemModel(0, "minecraft:emerald", "<#f1a66b>Balance", List.of("<#4ddb93>You have <#f1d46B>%amount%"), 0);
    globalItemPrevious = new ItemModel(0, "minecraft:arrow", "<#4ddb93>Previous", List.of(""), 0);
    globalItemClose = new ItemModel(0, "minecraft:barrier", "<#eb4747>Close", List.of(""), 0);
    globalItemNext = new ItemModel(0, "minecraft:arrow", "<#4ddb93>Next", List.of(""), 0);

    add1 = new ItemModel(21, "item:1:minecraft:lime_stained_glass_pane", "<#4ddb93>Add 1", List.of(""), 0);
    add8 = new ItemModel(20, "item:8:minecraft:lime_stained_glass_pane", "<#4ddb93>Add 8", List.of(""), 0);
    add16 = new ItemModel(20, "item:16:minecraft:lime_stained_glass_pane", "<#4ddb93>Add 16", List.of(""), 0);
    add64 = new ItemModel(19, "item:64:minecraft:lime_stained_glass_pane", "<#4ddb93>Add 64", List.of(""), 0);
    remove1 = new ItemModel(23, "item:1:minecraft:red_stained_glass_pane", "<#eb4747>Remove 1", List.of(""), 0);
    remove8 = new ItemModel(24, "item:8:minecraft:red_stained_glass_pane", "<#eb4747>Remove 8", List.of(""), 0);
    remove16 = new ItemModel(24, "item:16:minecraft:red_stained_glass_pane", "<#eb4747>Remove 16", List.of(""), 0);
    remove64 = new ItemModel(25, "item:64:minecraft:red_stained_glass_pane", "<#eb4747>Remove 64", List.of(""), 0);

    menuBuyAndSell = new BuyAndSellConfig();
  }

  /**
   * Resolves an item model with a fallback — replaces the 6 identical getGlobalX() methods.
   */
  public static ItemModel resolve(ItemModel override, ItemModel fallback) {
    if (override == null) return fallback;
    String item = override.getItem();
    if (item == null || item.isEmpty()) return fallback;
    return override;
  }
}

