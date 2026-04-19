package com.kingpixel.ultrashop.infrastructure.config;

import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import lombok.Data;

import java.util.List;

/**
 * POJO for the buy/sell confirmation menu config.
 * Extracted from Lang — a menu config does not belong inside language strings.
 */
@Data
public class BuyAndSellConfig {
  private int rows;
  private String titleBuy;
  private String titleSell;
  private int productSlot;
  private ItemModel itemConfirm;
  private ItemModel itemClose;
  private ItemModel itemCancel;
  private List<PanelsConfig> panels;

  public BuyAndSellConfig() {
    this.rows = 6;
    this.titleBuy = "&aBuy %amount%";
    this.titleSell = "&cSell %amount%";
    this.productSlot = 22;
    this.itemConfirm = new ItemModel(39, "minecraft:lime_stained_glass_pane", "&aConfirm", List.of(), 0);
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.itemCancel = new ItemModel(41, "minecraft:red_stained_glass_pane", "&cCancel", List.of(), 0);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }
}

