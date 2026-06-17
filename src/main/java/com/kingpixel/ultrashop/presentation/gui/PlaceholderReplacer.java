package com.kingpixel.ultrashop.presentation.gui;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.PriceEntry;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.StockMode;
import com.kingpixel.ultrashop.domain.model.UserInfo;
import com.kingpixel.ultrashop.domain.model.shop.ShopReference;
import com.kingpixel.ultrashop.domain.service.PriceCalculator;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces placeholder tokens in lore/text strings with actual values.
 */
public final class PlaceholderReplacer {

  private PlaceholderReplacer() {
  }

  /**
   * Replaces all product-related placeholders in a string.
   */
  public static String replace(String text, Product product, ServerPlayerEntity player,
                               ShopReference shop, int amount, ShopConfig config, String playerBalance) {
    if (text == null || text.isEmpty()) return "";

    if (text.contains("%buy%")) {
      Map<EconomyUse, BigDecimal> buyPrices = PriceCalculator.getBuyPrices(product, player, amount, shop, config);
      StringBuilder buySb = new StringBuilder();
      for (Map.Entry<EconomyUse, BigDecimal> e : buyPrices.entrySet()) {
        buySb.append(EconomyApi.formatMoney(e.getValue(), e.getKey())).append(" ");
      }
      text = text.replace("%buy%", buySb.toString().trim());
    }
    if (text.contains("%sell%")) {
      Map<EconomyUse, BigDecimal> sellPrices = PriceCalculator.getSellPrices(product, amount, shop);
      StringBuilder sellSb = new StringBuilder();
      for (Map.Entry<EconomyUse, BigDecimal> e : sellPrices.entrySet()) {
        sellSb.append(EconomyApi.formatMoney(e.getValue(), e.getKey())).append(" ");
      }
      text = text.replace("%sell%", sellSb.toString().trim());
    }
    if (text.contains("%amount%")) {
      text = text.replace("%amount%", String.valueOf(amount));
    }
    if (text.contains("%pack%")) {
      text = text.replace("%pack%", String.valueOf(product.getItemStack().getCount()));
    }
    if (text.contains("%discount%")) {
      float discount = PriceCalculator.getDiscount(product, player, shop, config);
      text = text.replace("%discount%", discount > 0f ? discount + "%" : "");
    }

    // Cooldown-limit placeholders (player quota)
    if (text.contains("%limit%") || text.contains("%bought%") || text.contains("%remaining%") || text.contains("%cooldown_time%")) {
      boolean hasLimit = product.getUuid() != null && product.getMax() != null;
      if (hasLimit) {
        ShopContext ctx = ShopContext.get();
        UserInfo userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
        int bought = userInfo != null ? userInfo.getActualProductLimit(product) : 0;
        int max = product.getMax();
        int remaining = Math.max(0, max - bought);
        long cooldownMs = userInfo != null ? userInfo.getProductCooldown(product) : System.currentTimeMillis();
        String readyMsg = ctx.getLang().getCooldownReady();
        if (readyMsg == null) readyMsg = "<#2ecc71>Ready";
        String cooldownStr = cooldownMs > System.currentTimeMillis()
          ? PlayerUtils.getCooldown(cooldownMs)
          : readyMsg;

        text = text.replace("%limit%", String.valueOf(max));
        text = text.replace("%bought%", String.valueOf(bought));
        text = text.replace("%remaining%", String.valueOf(remaining));
        text = text.replace("%cooldown_time%", cooldownStr);
      } else {
        text = text.replace("%limit%", "∞");
        text = text.replace("%bought%", "0");
        text = text.replace("%remaining%", "∞");
        text = text.replace("%cooldown_time%", "");
      }
    }

    // Dedicated stock placeholders
    if (text.contains("%stock_remaining%") || text.contains("%stock_limit%") || text.contains("%stock_mode%")
        || text.contains("%remaining%") || text.contains("%limit%")) {
      boolean hasStock = product.hasStockControl();
      if (hasStock) {
        ShopContext ctx = ShopContext.get();
        long limit = product.getStockAmount();
        StockMode mode = product.getStockMode();
        long remaining = ctx.getRepositories().getStockRepository().getRemaining(
          player.getUuid(),
          product.getUuid(),
          mode,
          limit
        );
        text = text.replace("%stock_remaining%", String.valueOf(remaining));
        text = text.replace("%stock_limit%", String.valueOf(limit));
        text = text.replace("%stock_mode%", mode.name());

        // Backward-compat for templates still using %remaining%/%limit% for stock
        text = text.replace("%remaining%", String.valueOf(remaining));
        text = text.replace("%limit%", String.valueOf(limit));
      } else {
        text = text.replace("%stock_remaining%", "∞");
        text = text.replace("%stock_limit%", "∞");
        text = text.replace("%stock_mode%", "");
      }
    }

    text = text.replace("%removebuy%", "")
      .replace("%removesell%", "")
      .replace("%removediscount%", "")
      .replace("%removelimit%", "")
      .replace("%removestock%", "")
      .replace("%balance%", playerBalance != null ? playerBalance : "");

    return text;
  }

  /**
   * Filters lore lines based on product capabilities and action context.
   */
  public static List<String> filterLore(List<String> loreTemplate, Product product,
                                        ServerPlayerEntity player, ShopReference shop,
                                        ShopConfig config, ActionShop actionShop) {
    List<String> filtered = new ArrayList<>();
    boolean hasLimit = product.getUuid() != null && product.getMax() != null;
    boolean hasStock = product.hasStockControl();

    for (String line : loreTemplate) {
      if (line == null || line.isEmpty()) {
        filtered.add(line);
        continue;
      }

      boolean notSellable = !product.isSellable();
      if (notSellable && (line.contains("%sell%") || line.contains("%removesell%"))) continue;

      boolean notBuyable = !product.isBuyable();
      if (notBuyable && (line.contains("%buy%") || line.contains("%removebuy%"))) continue;

      float discount = PriceCalculator.getDiscount(product, player, shop, config);
      if (discount <= 0f && line.contains("%removediscount%")) continue;

      // Hide limit lines when product has no limits
      if (!hasLimit && line.contains("%removelimit%")) continue;

      // Hide stock lines when product has no stock control
      if (!hasStock && line.contains("%removestock%")) continue;

      if (actionShop != null) {
        if (actionShop == ActionShop.BUY && (line.contains("%sell%") || line.contains("%removesell%"))) continue;
        if (actionShop == ActionShop.SELL && (line.contains("%buy%") || line.contains("%removebuy%"))) continue;
      }

      filtered.add(line);
    }

    return filtered;
  }

  /**
   * Builds a balance string from a product's effective prices.
   */
  public static String buildBalanceString(Product product, ShopReference shop, ServerPlayerEntity player) {
    StringBuilder sb = new StringBuilder();
    List<PriceEntry> entries = product.getEffectivePrices(shop);
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (PriceEntry entry : entries) {
      String key = entry.getEconomy().getEconomyId() + ":" + entry.getEconomy().getCurrency();
      if (seen.add(key)) {
        BigDecimal bal = EconomyApi.getBalance(player.getUuid(), entry.getEconomy());
        sb.append(EconomyApi.formatMoney(bal, entry.getEconomy())).append(" ");
      }
    }
    return sb.toString().trim();
  }
}
