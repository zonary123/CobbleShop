package com.kingpixel.cobbleshop.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.UIUtils;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:54
 */
public class MenuBuyAndSell {
  private final int rows;
  private final String titleBuy;
  private final String titleSell;
  private final int productSlot;
  private final ItemModel itemConfirm;
  private final ItemModel itemClose;
  private final ItemModel itemCancel;
  private final List<PanelsConfig> panels;

  public MenuBuyAndSell() {
    this.rows = 6;
    this.titleBuy = "&aBuy";
    this.titleSell = "&cSell";
    this.productSlot = 22;
    this.itemConfirm = new ItemModel(39, "minecraft:lime_stained_glass_pane", "&aConfirm", List.of(), 0);
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.itemCancel = new ItemModel(41, "minecraft:red_stained_glass_pane", "&cCancel", List.of(), 0);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public void open(ServerPlayerEntity player, Shop shop, Product product, int amount, ActionShop actionShop, ShopOptionsApi options, Config config) {
    if (!product.isBuyable() && actionShop.equals(ActionShop.BUY)) return;
    if (!product.isSellable() && actionShop.equals(ActionShop.SELL)) return;

    ChestTemplate template = ChestTemplate
      .builder(rows)
      .build();

    PanelsConfig.applyConfig(template, panels);

    // Product Icon
    if (UIUtils.isInside(productSlot, rows)) {
      template.set(productSlot, product.getIcon(shop, actionShop, amount, options, config));
    }

    // Remove and Add Buttons
    int totalStack = product.getStack();

    if (totalStack != 1) {
      // Add and Remove 1
      if (UIUtils.isInside(CobbleShop.lang.getAdd1().getSlot(), rows)) {
        template.set(CobbleShop.lang.getAdd1().getSlot(), CobbleShop.lang.getAdd1().getButton(action -> {
          open(player, shop, product, amount + 1, actionShop, options, config);
        }));
      }
      if (UIUtils.isInside(CobbleShop.lang.getRemove1().getSlot(), rows)) {
        template.set(CobbleShop.lang.getRemove1().getSlot(), CobbleShop.lang.getRemove1().getButton(action -> {
          if (amount > 1) open(player, shop, product, Math.max(amount - 1, 1), actionShop, options, config);
        }));
      }

      if (totalStack == 16) {
        if (UIUtils.isInside(CobbleShop.lang.getAdd8().getSlot(), rows)) {
          template.set(CobbleShop.lang.getAdd8().getSlot(), CobbleShop.lang.getAdd8().getButton(action -> {
            open(player, shop, product, amount + 8, actionShop, options, config);
          }));
        }
        if (UIUtils.isInside(CobbleShop.lang.getRemove8().getSlot(), rows)) {
          template.set(CobbleShop.lang.getRemove8().getSlot(), CobbleShop.lang.getRemove8().getButton(action -> {
            if (amount > 8) open(player, shop, product, Math.max(amount - 8, 1), actionShop, options, config);
          }));
        }
      }

      if (totalStack == 64) {
        if (UIUtils.isInside(CobbleShop.lang.getAdd16().getSlot(), rows)) {
          template.set(CobbleShop.lang.getAdd16().getSlot(), CobbleShop.lang.getAdd16().getButton(action -> {
            open(player, shop, product, amount + 16, actionShop, options, config);
          }));
        }
        if (UIUtils.isInside(CobbleShop.lang.getRemove16().getSlot(), rows)) {
          template.set(CobbleShop.lang.getRemove16().getSlot(), CobbleShop.lang.getRemove16().getButton(action -> {
            if (amount > 16) open(player, shop, product, Math.max(amount - 16, 1), actionShop, options, config);
          }));
        }
      }

      if (UIUtils.isInside(CobbleShop.lang.getAdd64().getSlot(), rows)) {
        template.set(CobbleShop.lang.getAdd64().getSlot(), CobbleShop.lang.getAdd64().getButton(action -> {
          open(player, shop, product, amount + 64, actionShop, options, config);
        }));
      }

      if (UIUtils.isInside(CobbleShop.lang.getRemove64().getSlot(), rows)) {
        template.set(CobbleShop.lang.getRemove64().getSlot(), CobbleShop.lang.getRemove64().getButton(action -> {
          open(player, shop, product, Math.max(amount - 64, 1), actionShop, options, config);
        }));
      }
    }

    // Extra Buttons
    if (UIUtils.isInside(itemCancel.getSlot(), rows * 9)) {
      template.set(itemCancel.getSlot(), itemCancel.getButton(action -> {
        shop.open(player, options, config, 0, shop);
      }));
    }

    if (UIUtils.isInside(itemClose.getSlot(), rows * 9)) {
      template.set(itemClose.getSlot(), itemClose.getButton(action -> {
        shop.open(player, options, config, 0, shop);
      }));
    }

    if (UIUtils.isInside(itemConfirm.getSlot(), rows * 9)) {
      template.set(itemConfirm.getSlot(), itemConfirm.getButton(action -> {
        if (actionShop.equals(ActionShop.BUY)) {
          shop.getType().buyProduct(player, product, shop, amount, options, config);
        } else {
          product.sell(player, shop, amount);
        }
      }));
    }

    GooeyPage page = GooeyPage
      .builder()
      .template(template)
      .title(AdventureTranslator.toNative(actionShop.equals(ActionShop.BUY) ? titleBuy : titleSell))
      .build();

    UIManager.openUIForcefully(player, page);
  }
}
