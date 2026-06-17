package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.Sound;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import com.kingpixel.ultrashop.domain.service.PriceCalculator;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds GooeyButton instances from Product data.
 * Extracted from Product.getIcon() — presentation logic only.
 */
public final class ProductRenderer {

  private ProductRenderer() {
  }

  /**
   * Creates a clickable product button for the shop GUI.
   */
  public static GooeyButton createButton(Product product, ServerPlayerEntity player,
                                         Shop shop, ActionShop actionShop, int amount,
                                         ShopConfig config, NavigationContext nav,
                                         boolean withClose, String playerBalance) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();

    String finalDisplay = product.getDisplay() != null ? product.getDisplay() : product.getProduct();
    ItemChance itemChance = new ItemChance(finalDisplay, 0);
    String title = product.getDisplayname() != null ? product.getDisplayname() : itemChance.getTitle();

    // Build lore
    List<String> loreTemplate = new ArrayList<>(lang.getInfoProduct());
    List<String> filteredLore = PlaceholderReplacer.filterLore(loreTemplate, product, player, shop, config, actionShop);
    List<String> lore = new ArrayList<>();

    for (String line : filteredLore) {
      lore.add(PlaceholderReplacer.replace(line, product, player, shop, amount, config, playerBalance));
    }

    // Handle %info% replacement with product custom lore
    injectCustomLore(lore, product);

    // Build ItemStack
    ItemStack itemStack = itemChance.getItemStack();
    if (amount == itemStack.getCount()) itemStack.setCount(amount);
    if (itemStack.getCount() == 0) itemStack.setCount(1);
    if (product.getCustomModelData() != null && itemStack.get(DataComponentTypes.CUSTOM_MODEL_DATA) == null) {
      itemStack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(product.getCustomModelData()));
    }

    return GooeyButton.builder()
      .display(itemStack)
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(colorProduct(shop) + title))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
      .onClick(action -> handleClick(action, product, player, shop, amount, config, nav, withClose))
      .build();
  }

  // --- Private helpers ---

  private static void handleClick(ca.landonjw.gooeylibs2.api.button.ButtonAction action,
                                  Product product, ServerPlayerEntity player, Shop shop,
                                  int amount, ShopConfig config, NavigationContext nav, boolean withClose) {
    try {
      ShopContext ctx = ShopContext.get();
      ActionShop shopAction = switch (action.getClickType()) {
        case LEFT_CLICK, SHIFT_LEFT_CLICK -> ActionShop.BUY;
        case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> ActionShop.SELL;
        default -> ActionShop.BUY;
      };

      if (!PriceCalculator.hasPermission(product, player)) {
        PlayerUtils.sendMessage(player, ctx.getLang().getMessageNotBuyPermission(),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);
        return;
      }

      if (shopAction == ActionShop.BUY && product.hasStockControl()) {
        long remaining = ctx.getRepositories().getStockRepository().getRemaining(
          player.getUuid(),
          product.getUuid(),
          product.getStockMode(),
          product.getStockAmount()
        );
        if (remaining <= 0) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageOutOfStock(),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }
      }

      // Block sell if sell price > buy price (exploit prevention)
      if (shopAction == ActionShop.SELL && !PriceCalculator.canSell(product, player, shop, config)) {
        PlayerUtils.sendMessage(player, ctx.getLang().getMessageBuyPriceLessThanSell(),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);
        return;
      }

      var userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
      if (userInfo != null && !userInfo.canBuy(product)) {
        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageYouCantBuyNow()
            .replace("%limit%", String.valueOf(product.getMax()))
            .replace("%time%", PlayerUtils.getCooldown(userInfo.getProductCooldown(product))),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);
        return;
      }

      new Sound(soundOpen(shop)).playSoundPlayer(player);
      BuyAndSellMenuBuilder.open(player, nav, product, amount, shopAction, config, withClose);
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error handling product click for " + product.getProduct() + ": " + e.getMessage());
    }
  }

  private static String colorProduct(Shop shop) {
    DisplayConfig display = shop.getDisplayConfig();
    String color = display != null ? display.getColorProduct() : null;
    return color != null ? color : "";
  }

  private static String soundOpen(Shop shop) {
    SoundConfig sound = shop.getSoundConfig();
    return sound != null ? sound.getSoundOpen() : null;
  }

  private static void injectCustomLore(List<String> lore, Product product) {
    boolean replaced = false;
    if (product.getLore() != null) {
      for (int i = 0; i < lore.size(); i++) {
        if (lore.get(i).contains("%info%")) {
          lore.remove(i);
          lore.addAll(i, product.getLore());
          replaced = true;
          break;
        }
      }
    }
    if (!replaced) {
      ShopContext ctx = ShopContext.get();
      lore.replaceAll(s -> s.contains("%info%") ? ctx.getLang().getNotExtraInfo() : s);
    }
    lore.removeIf(s -> s.contains("%info%"));
  }
}


