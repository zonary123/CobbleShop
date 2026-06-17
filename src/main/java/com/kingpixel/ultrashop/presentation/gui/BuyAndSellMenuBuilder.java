package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UIUtils;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.service.TransactionService;
import com.kingpixel.ultrashop.infrastructure.config.BuyAndSellConfig;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Builds and opens the buy/sell confirmation menu.
 * Extracted from MenuBuyAndSell — presentation only.
 */
public final class BuyAndSellMenuBuilder {

  private BuyAndSellMenuBuilder() {
  }

  public static void open(ServerPlayerEntity player, NavigationContext nav, Product product,
                          int amount, ActionShop actionShop, ShopConfig config, boolean withClose) {
    open(new MenuRequest(player, nav, product, amount, actionShop, config, withClose));
  }

  private static void open(MenuRequest request) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    BuyAndSellConfig menuConfig = lang.getMenuBuyAndSell();
    Shop shop = request.nav().current();

    ctx.getAsyncContext().runAsync(() -> buildAndOpenMenu(request, ctx, lang, menuConfig, shop));
  }

  private static void buildAndOpenMenu(MenuRequest request, ShopContext ctx, LangConfig lang,
                                       BuyAndSellConfig menuConfig, Shop shop) {
    try {
      if (!request.product().isBuyable() && request.actionShop() == ActionShop.BUY) {
        return;
      }

      ChestTemplate template = ChestTemplate.builder(menuConfig.getRows()).build();
      PanelsConfig.applyConfig(template, menuConfig.getPanels());
      applyNavigationButtons(template, menuConfig, request);
      applyProductPreview(template, menuConfig, request, shop);
      applyConfirmButton(template, menuConfig, request, shop, ctx);
      applyAmountButtons(template, lang, request);

      String title = resolveTitle(menuConfig, request.amount(), request.actionShop());
      GooeyPage page = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(title))
        .build();

      ctx.runOnServer(() -> UIManager.openUIForcefully(request.player(), page));
    } catch (Exception e) {
      UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error opening buy/sell menu: " + e.getMessage());
    }
  }

  private static void applyNavigationButtons(ChestTemplate template, BuyAndSellConfig menuConfig,
                                             MenuRequest request) {
    menuConfig.getItemCancel().applyTemplate(template, getButton(menuConfig.getItemCancel(),
      action -> reopenShopMenu(request)));
    menuConfig.getItemClose().applyTemplate(template, getButton(menuConfig.getItemClose(),
      action -> reopenShopMenu(request)));
  }

  private static void applyProductPreview(ChestTemplate template, BuyAndSellConfig menuConfig,
                                          MenuRequest request, Shop shop) {
    if (!UIUtils.isInside(menuConfig.getProductSlot(), menuConfig.getRows())) {
      return;
    }
    String playerBalance = PlaceholderReplacer.buildBalanceString(request.product(), shop, request.player());
    template.set(menuConfig.getProductSlot(),
      ProductRenderer.createButton(request.product(), request.player(), shop, request.actionShop(), request.amount(),
        request.config(), request.nav(), request.withClose(), playerBalance));
  }

  private static void applyConfirmButton(ChestTemplate template, BuyAndSellConfig menuConfig,
                                         MenuRequest request, Shop shop, ShopContext ctx) {
    menuConfig.getItemConfirm().applyTemplate(template, getButton(menuConfig.getItemConfirm(), action ->
      ctx.getAsyncContext().runAsync(() -> {
        handleConfirmAction(request, shop, ctx);
        reopenShopMenu(request);
      })));
  }

  private static void handleConfirmAction(MenuRequest request, Shop shop, ShopContext ctx) {
    if (request.actionShop() == ActionShop.BUY) {
      handleBuyAction(request, shop, ctx);
      return;
    }
    ctx.runOnServer(() -> TransactionService.sell(request.player(), request.product(), shop, request.amount(), request.config()));
  }

  private static void handleBuyAction(MenuRequest request, Shop shop, ShopContext ctx) {
    int buyAmount = resolveBuyAmount(request, ctx);
    if (buyAmount <= 0) {
      return;
    }
    if (!tryConsumeStock(request.player(), request.product(), buyAmount, ctx)) {
      return;
    }
    ctx.runOnServer(() -> TransactionService.buy(request.player(), request.product(), shop, buyAmount, request.config(), true));
  }

  private static boolean tryConsumeStock(ServerPlayerEntity player, Product product, int amount, ShopContext ctx) {
    Integer stockAmount = product.getStockAmount();
    if (!product.hasStockControl() || stockAmount == null) {
      return true;
    }
    boolean consumed = ctx.getRepositories().getStockRepository().tryConsume(
      player.getUuid(),
      product.getUuid(),
      product.getStockMode(),
      amount,
      stockAmount
    );
    if (consumed) {
      return true;
    }
    long remaining = ctx.getRepositories().getStockRepository().getRemaining(
      player.getUuid(),
      product.getUuid(),
      product.getStockMode(),
      stockAmount
    );
    PlayerUtils.sendMessage(player,
      ctx.getLang().getMessageNotEnoughStock().replace("%remaining%", String.valueOf(remaining)),
      ctx.getLang().getPrefix(), TypeMessage.CHAT);
    return false;
  }

  private static int resolveBuyAmount(MenuRequest request, ShopContext ctx) {
    int buyAmount = clampByStock(request, ctx);
    if (buyAmount <= 0) {
      return 0;
    }
    return clampByPlayerLimit(request, ctx, buyAmount);
  }

  private static int clampByStock(MenuRequest request, ShopContext ctx) {
    Integer stockAmount = request.product().getStockAmount();
    if (!request.product().hasStockControl() || stockAmount == null) {
      return request.amount();
    }
    long remaining = ctx.getRepositories().getStockRepository().getRemaining(
      request.player().getUuid(),
      request.product().getUuid(),
      request.product().getStockMode(),
      stockAmount
    );
    if (remaining <= 0) {
      PlayerUtils.sendMessage(request.player(), ctx.getLang().getMessageOutOfStock(), ctx.getLang().getPrefix(), TypeMessage.CHAT);
      return 0;
    }
    return (int) Math.min(request.amount(), remaining);
  }

  private static int clampByPlayerLimit(MenuRequest request, ShopContext ctx, int buyAmount) {
    Integer maxLimit = request.product().getMax();
    if (request.product().getUuid() == null || maxLimit == null) {
      return buyAmount;
    }
    com.kingpixel.ultrashop.domain.model.UserInfo userInfo = ctx.getRepositories().getUserRepository().findByUuid(request.player().getUuid());
    if (userInfo == null) {
      userInfo = new com.kingpixel.ultrashop.domain.model.UserInfo(request.player().getUuid(), request.player().getGameProfile().getName());
    }
    int actual = userInfo.getActualProductLimit(request.product());
    if (actual >= maxLimit) {
      return 0;
    }
    return Math.min(buyAmount, maxLimit - actual);
  }

  private static void applyAmountButtons(ChestTemplate template, LangConfig lang, MenuRequest request) {
    if (request.product().getMaxStack() == 1) {
      return;
    }
    addAmountModifier(template, lang.getAdd1(), 1, true, request);
    addAmountModifier(template, lang.getRemove1(), 1, false, request);
    addAmountModifier(template, lang.getAdd8(), 8, true, request);
    addAmountModifier(template, lang.getRemove8(), 8, false, request);
    addAmountModifier(template, lang.getAdd16(), 16, true, request);
    addAmountModifier(template, lang.getRemove16(), 16, false, request);
    addAmountModifier(template, lang.getAdd64(), 64, true, request);
    addAmountModifier(template, lang.getRemove64(), 64, false, request);
  }

  private static String resolveTitle(BuyAndSellConfig menuConfig, int amount, ActionShop actionShop) {
    return (actionShop == ActionShop.BUY ? menuConfig.getTitleBuy() : menuConfig.getTitleSell())
      .replace("%amount%", String.valueOf(amount));
  }

  private static void reopenShopMenu(MenuRequest request) {
    ShopMenuBuilder.open(request.player(), request.nav(), request.config(), request.withClose());
  }

  private static void addAmountModifier(ChestTemplate template, ItemModel item, int delta, boolean add,
                                        MenuRequest request) {
    if (item.getSlot() < 0) return;
    item.applyTemplate(template, getButton(item, a -> {
      int newAmount = add ? request.amount() + delta : Math.max(request.amount() - delta, 1);
      open(new MenuRequest(request.player(), request.nav(), request.product(), newAmount,
        request.actionShop(), request.config(), request.withClose()));
    }));
  }

  private static GooeyButton getButton(ItemModel model, java.util.function.Consumer<ca.landonjw.gooeylibs2.api.button.ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(model.getItemStack())
      .onClick(onClick::accept)
      .build();
  }

  private record MenuRequest(ServerPlayerEntity player, NavigationContext nav, Product product, int amount,
                             ActionShop actionShop, ShopConfig config, boolean withClose) {
  }
}
