package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UIUtils;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.service.PriceCalculator;
import com.kingpixel.ultrashop.domain.service.TransactionService;
import com.kingpixel.ultrashop.infrastructure.config.BuyAndSellConfig;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/**
 * Builds and opens the buy/sell confirmation menu.
 * Extracted from MenuBuyAndSell — presentation only.
 */
public final class BuyAndSellMenuBuilder {

  private BuyAndSellMenuBuilder() {
  }

  public static void open(ServerPlayerEntity player, NavigationContext nav, Product product,
                          int amount, ActionShop actionShop, ShopConfig config, boolean withClose) {

    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    BuyAndSellConfig menuConfig = lang.getMenuBuyAndSell();
    Shop shop = nav.current();

    ctx.getAsyncContext().runAsync(() -> {
      try {
        if (!product.isBuyable() && actionShop == ActionShop.BUY) return;

        ChestTemplate template = ChestTemplate.builder(menuConfig.getRows()).build();
        PanelsConfig.applyConfig(template, menuConfig.getPanels());

        // Cancel button
        menuConfig.getItemCancel().applyTemplate(template, menuConfig.getItemCancel().getButton(action -> {
          ShopMenuBuilder.open(player, nav, config, withClose);
        }));

        // Close button
        menuConfig.getItemClose().applyTemplate(template, menuConfig.getItemClose().getButton(action -> {
          ShopMenuBuilder.open(player, nav, config, withClose);
        }));

        // Product icon
        if (UIUtils.isInside(menuConfig.getProductSlot(), menuConfig.getRows())) {
          String playerBalance = PlaceholderReplacer.buildBalanceString(product, shop, player);
          template.set(menuConfig.getProductSlot(),
            ProductRenderer.createButton(product, player, shop, actionShop, amount, config, nav, withClose, playerBalance));
        }

        // Confirm button
        menuConfig.getItemConfirm().applyTemplate(template, menuConfig.getItemConfirm().getButton(action -> {
          ctx.getAsyncContext().runAsync(() -> {
            if (actionShop == ActionShop.BUY) {
              int buyAmount = amount;
              if (product.getUuid() != null) {
                var userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
                if (userInfo != null) {
                  int actual = userInfo.getActualProductLimit(product);
                  int max = product.getMax();
                  if (actual < max) {
                    buyAmount = Math.min(buyAmount, max - actual);
                    TransactionService.buy(player, product, shop, buyAmount, config);
                  }
                } else {
                  TransactionService.buy(player, product, shop, buyAmount, config);
                }
              } else {
                TransactionService.buy(player, product, shop, buyAmount, config);
              }
            } else {
              ctx.runOnServer(() -> TransactionService.sell(player, product, shop, amount, config));
            }
            ShopMenuBuilder.open(player, nav, config, withClose);
          });
        }));

        // Amount modifier buttons — each reopens the menu with the new amount
        int totalStack = product.getMaxStack();
        if (totalStack != 1) {
          addAmountModifier(template, lang.getAdd1(), 1, true, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getRemove1(), 1, false, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getAdd8(), 8, true, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getRemove8(), 8, false, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getAdd16(), 16, true, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getRemove16(), 16, false, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getAdd64(), 64, true, amount, product, player, nav, actionShop, config, withClose);
          addAmountModifier(template, lang.getRemove64(), 64, false, amount, product, player, nav, actionShop, config, withClose);
        }

        String title = (actionShop == ActionShop.BUY ? menuConfig.getTitleBuy() : menuConfig.getTitleSell())
          .replace("%amount%", String.valueOf(amount));

        GooeyPage page = GooeyPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title))
          .build();

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private static void addAmountModifier(ChestTemplate template, ItemModel item, int delta, boolean add,
                                        int currentAmount, Product product,
                                        ServerPlayerEntity player, NavigationContext nav,
                                        ActionShop actionShop, ShopConfig config, boolean withClose) {
    if (item.getSlot() < 0) return;
    item.applyTemplate(template, item.getButton(a -> {
      int newAmount = add ? currentAmount + delta : Math.max(currentAmount - delta, 1);
      open(player, nav, product, newAmount, actionShop, config, withClose);
    }));
  }
}

