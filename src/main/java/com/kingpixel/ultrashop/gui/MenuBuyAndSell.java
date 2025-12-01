package com.kingpixel.ultrashop.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopApi;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.config.Config;
import com.kingpixel.ultrashop.database.DataBaseFactory;
import com.kingpixel.ultrashop.models.ActionShop;
import com.kingpixel.ultrashop.models.Product;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UIUtils;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;

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

  public void open(ServerPlayerEntity player, Stack<Shop> stack, Product product, int amount, ActionShop actionShop,
                   ShopOptionsApi options, Config config, boolean withClose) {
    CompletableFuture.runAsync(() -> {
        BigDecimal buyPrice = product.getBuyPrice(player, amount, stack.peek(), config);
        if (buyPrice.compareTo(BigDecimal.ZERO) > 0) {
          if (buyPrice.compareTo(product.getSellPrice(amount)) < 0) {
            PlayerUtils.sendMessage(
              player,
              UltraShop.lang.getMessageBuyPriceLessThanSell(),
              UltraShop.lang.getPrefix(),
              TypeMessage.CHAT
            );
            return;
          }
        }

        if (!product.isBuyable() && actionShop.equals(ActionShop.BUY)) return;
        if ((!product.isSellable() || !product.canSell(player, stack.peek(), options)) && actionShop.equals(ActionShop.SELL)) {
          PlayerUtils.sendMessage(
            player,
            UltraShop.lang.getMessageNotSell(),
            UltraShop.lang.getPrefix(),
            TypeMessage.CHAT
          );
          return;
        }


        ChestTemplate template = ChestTemplate
          .builder(rows)
          .build();

        PanelsConfig.applyConfig(template, panels);

        // Product Icon
        if (UIUtils.isInside(productSlot, rows)) {
          var economyUse = stack.peek().getEconomy();
          BigDecimal balance = EconomyApi.getBalance(player.getUuid(), economyUse);
          String playerBalance = EconomyApi.formatMoney(balance, economyUse);
          template.set(productSlot, product.getIcon(player, stack, actionShop, amount, options, config, withClose, playerBalance));
        }

        // Remove and Add Buttons
        int totalStack = product.getStack();

        if (totalStack != 1) {
          // Add and Remove 1
          putButton(player, stack, product, amount, actionShop, options, config, withClose, template, UltraShop.lang.getAdd1(), 1, UltraShop.lang.getRemove1());
          // Add and Remove 8
          putButton(player, stack, product, amount, actionShop, options, config, withClose, template, UltraShop.lang.getAdd8(), 8, UltraShop.lang.getRemove8());
          // Add and Remove 16
          putButton(player, stack, product, amount, actionShop, options, config, withClose, template, UltraShop.lang.getAdd16(), 16, UltraShop.lang.getRemove16());
          // Add and Remove 64
          putButton(player, stack, product, amount, actionShop, options, config, withClose, template, UltraShop.lang.getAdd64(), 64, UltraShop.lang.getRemove64());
        }

        // Extra Buttons
        itemCancel.applyTemplate(template, itemCancel.getButton(action -> {
          Config.manageOpenShop(player, options, config, null, stack, null, withClose);
        }));

        itemClose.applyTemplate(template, itemClose.getButton(action -> {
          Config.manageOpenShop(player, options, config, null, stack, null, withClose);
        }));

        itemConfirm.applyTemplate(template, itemConfirm.getButton(action -> {
          Shop shop = stack.peek();
          if (actionShop.equals(ActionShop.BUY)) {
            int finalAmount = amount;
            if (product.getUuid() != null) {
              var userinfo = DataBaseFactory.INSTANCE.getUserInfo(player);
              int actual = userinfo.getActualProductLimit(product);
              int max = product.getMax();
              if (actual >= max) UIManager.closeUI(player);
              finalAmount = Math.min(finalAmount, max);
              if (ShopApi.getMainConfig().isDebug()) {
                CobbleUtils.LOGGER.info(UltraShop.MOD_ID,
                  "Limit: " + actual + " / " + max + " - Uuid: " + product.getUuid() + " - Amount: " + finalAmount);
              }
            }
            shop.getType().buyProduct(player, product, shop, finalAmount, options, config, stack, withClose);
          } else {
            product.sell(player, shop, amount, product, options, config, stack, withClose);
          }
        }));

        GooeyPage page = GooeyPage
          .builder()
          .template(template)
          .title(AdventureTranslator.toNative((actionShop.equals(ActionShop.BUY) ? titleBuy : titleSell).replace("%amount%", amount +
            "")))
          .build();

        UIManager.openUIForcefully(player, page);
      }, UltraShop.SHOP_EXECUTOR)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

  private void putButton(ServerPlayerEntity player, Stack<Shop> stack, Product product, int amount, ActionShop actionShop, ShopOptionsApi options, Config config, boolean withClose, ChestTemplate template, ItemModel buttonAdd, int i, ItemModel buttonRemove) {
    buttonAdd.applyTemplate(template, buttonAdd.getButton(action -> {
      open(player, stack, product, amount + i, actionShop, options, config, withClose);
    }));

    buttonRemove.applyTemplate(template, buttonRemove.getButton(action -> {
      open(player, stack, product, Math.max(amount - i, 1), actionShop, options, config, withClose);
    }));
  }
}
