package com.kingpixel.cobbleshop.models;

import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleshop.adapters.ShopTypePermanent;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
public class Shop {
  private String Path;
  // Essential fields
  private String id;
  private String title;
  private String currency;
  private String closeCommand;
  private String soundOpen;
  private String soundClose;
  private int rows;
  private int globalDiscount;
  private ShopType type;
  private Rectangle rectangle;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private ItemModel itemBalance;
  private List<SubShop> subShops;
  private List<Product> products;
  private ItemModel itemPrevious;
  private ItemModel itemClose;
  private ItemModel itemNext;
  private List<PanelsConfig> panels;

  public Shop() {
    this.id = "shop";
    this.title = "Shop";
    this.currency = "emerald";
    this.closeCommand = "close";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = new ShopTypePermanent();
    this.rectangle = new Rectangle(rows);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    this.itemBalance = new ItemModel("");
    this.products = List.of();
    this.itemPrevious = new ItemModel("");
    this.itemClose = new ItemModel("");
    this.itemNext = new ItemModel("");
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public Shop(String id, ShopType type) {
    this.id = id;
    this.title = "Shop";
    this.currency = "emerald";
    this.closeCommand = "close";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = type;
    this.rectangle = new Rectangle(rows);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    this.itemBalance = new ItemModel("");
    this.products = List.of();
    this.itemPrevious = new ItemModel("");
    this.itemClose = new ItemModel("");
    this.itemNext = new ItemModel("");
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public void check() {
  }

  public void open(ServerPlayerEntity player, ShopOptionsApi options, Config config) {
  }
}
