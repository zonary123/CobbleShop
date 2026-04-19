package com.kingpixel.ultrashop.infrastructure.config;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.ultrashop.UltraShop;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POJO for config.json — no I/O logic, no GUI logic.
 */
@Data
public class ShopConfig {
  private boolean debug;
  private boolean saveTransactions;
  private String lang;
  private int rows;
  private String title;
  private String soundOpen;
  private String soundClose;
  private DataBaseConfig dataBase;
  private Map<String, Float> discounts;
  private ItemModel itemClose;
  private List<String> commands;
  private List<String> sellCommands;
  private int maxBuyAmount;
  private int transactionPageSize;
  private List<PanelsConfig> panels;

  // --- Web Dashboard ---
  private boolean webDashboardEnabled;
  private int webDashboardPort;
  private String webDashboardPassword;

  public ShopConfig() {
    // ...existing defaults...
    this.debug = false;
    this.saveTransactions = true;
    this.lang = "en";
    this.rows = 6;
    this.title = "Shop";
    this.soundOpen = "";
    this.soundClose = "";
    this.discounts = new HashMap<>();
    this.discounts.put("group.vip", 2.0f);
    this.dataBase = new DataBaseConfig();
    this.dataBase.setDatabase("ultrashop");
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.commands = new ArrayList<>();
    this.commands.add("shop");
    this.commands.add(UltraShop.MOD_ID);
    this.sellCommands = new ArrayList<>();
    this.sellCommands.add("sell");
    this.maxBuyAmount = 2304;
    this.transactionPageSize = 10;
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
    this.webDashboardEnabled = false;
    this.webDashboardPort = 8095;
    this.webDashboardPassword = "";
  }

  public void check() {
    if (commands == null || commands.isEmpty()) {
      commands = new ArrayList<>();
      commands.add("shop");
    }
    if (sellCommands == null || sellCommands.isEmpty()) {
      sellCommands = new ArrayList<>();
      sellCommands.add("sell");
    }
    if (discounts == null) discounts = new HashMap<>();
    if (dataBase == null) {
      dataBase = new DataBaseConfig();
      dataBase.setDatabase("ultrashop");
    }
    if (maxBuyAmount <= 0) maxBuyAmount = 2304;
    if (transactionPageSize <= 0) transactionPageSize = 10;
    if (webDashboardPort <= 0 || webDashboardPort > 65535) webDashboardPort = 8095;
    if (webDashboardPassword == null) webDashboardPassword = "";
  }
}
