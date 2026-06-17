package com.kingpixel.ultrashop.domain.service;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.domain.model.UserInfo;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopReference;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.infrastructure.index.SellProductIndex;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles buy, sell, and sellAll transactions with proper thread safety.
 *
 * <p>CRITICAL: Inventory modifications MUST run on the server thread.
 * Price calculations can run async.</p>
 */
public final class TransactionService {

  private static final String PLACEHOLDER_AMOUNT = "%amount%";
  private static final String PLACEHOLDER_PRICE = "%price%";
  private static final Map<UUID, Long> sellLock = new ConcurrentHashMap<>();

  private TransactionService() {
  }

  /**
   * Buy a product for a player. Charges ALL economies in the product's effective prices.
   * For backwards compatibility.
   */
  public static boolean buy(ServerPlayerEntity player, Product product, ShopReference shop, int amount,
                             ShopConfig config) {
    return buy(player, product, shop, amount, config, false);
  }

  /**
   * Buy a product for a player. Charges ALL economies in the product's effective prices.
   * If stockAlreadyReserved is true, the stock has already been checked and decremented in DB.
   */
  public static boolean buy(ServerPlayerEntity player, Product product, ShopReference shop, int amount,
                             ShopConfig config, boolean stockAlreadyReserved) {
    ShopContext ctx = ShopContext.get();
    synchronized (ctx.getTransactionLock(player.getUuid())) {
      ItemChance itemChance = buildItemChance(product);
      ItemStack itemStack = itemChance.getItemStack();

      if (!hasInventorySpace(player, product, amount, itemStack, ctx)) {
        if (stockAlreadyReserved) {
          releaseStock(player, product, amount, ctx);
        }
        return false;
      }

      Map<EconomyUse, BigDecimal> buyPrices = PriceCalculator.getBuyPrices(product, player, amount, shop, config);
      if (!canAffordBuy(player, itemChance, itemStack, amount, buyPrices, ctx)) {
        if (stockAlreadyReserved) {
          releaseStock(player, product, amount, ctx);
        }
        return false;
      }

      UserInfo userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
      if (userInfo != null && !userInfo.canBuy(product)) {
        long time = Math.max(0, (userInfo.getProductCooldown(product) - System.currentTimeMillis()) / 1000);
        String limitMsg = ctx.getLang().getMessageYouCantBuyNow()
          .replace("%limit%", String.valueOf(product.getMax()))
          .replace("%time%", String.valueOf(time));
        PlayerUtils.sendMessage(player, limitMsg, ctx.getLang().getPrefix(), TypeMessage.CHAT);
        if (stockAlreadyReserved) {
          releaseStock(player, product, amount, ctx);
        }
        return false;
      }

      if (!stockAlreadyReserved) {
        if (!tryConsumeStock(player, product, amount, ctx)) {
          return false;
        }
      }

      try {
        chargeBuyPrices(player, buyPrices);
        ItemChance.giveReward(player, itemChance, amount);
      } catch (Exception e) {
        releaseStock(player, product, amount, ctx);
        refundBuyPrices(player, buyPrices);
        UltraShop.LOGGER.error("Error completing buy transaction: " + e.getMessage());
        return false;
      }

      persistProductLimit(player, product, amount, ctx);
      saveTransactions(player, product, shop, amount, buyPrices, ActionShop.BUY, config, ctx);

      return true;
    }
  }

  /**
   * Sell a specific product from a player's inventory.
   */
  public static void sell(ServerPlayerEntity player, Product product, ShopReference shop, int amount,
                          ShopConfig config) {
    ShopContext ctx = ShopContext.get();
    synchronized (ctx.getTransactionLock(player.getUuid())) {
      // Safety: block selling if sell price > buy price (exploit prevention)
      if (!PriceCalculator.canSell(product, player, shop, config)) {
        UltraShop.LOGGER.warn("Blocked exploit sell attempt: {} tried to sell {} (sell > buy)",
          player.getGameProfile().getName(), product.getProduct());
        PlayerUtils.sendMessage(player, ctx.getLang().getMessageBuyPriceLessThanSell(),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);
        return;
      }

      ItemStack productTemplate = product.getItemStack();
      Map<EconomyUse, BigDecimal> sellPerUnit = PriceCalculator.getSellPricesPerUnit(product, shop);
      final int[] sold = {0};
      int remaining = amount;

      PlayerInventory inventory = player.getInventory();
      for (int i = 0; i < inventory.size() && remaining > 0; i++) {
        ItemStack slot = inventory.getStack(i);
        if (ItemStack.areItemsAndComponentsEqual(slot, productTemplate)) {
          int stackCount = slot.getCount();
          int toRemove = Math.min(stackCount, remaining);
          slot.decrement(toRemove);
          sold[0] += toRemove;
          remaining -= toRemove;
        }
      }

      if (sold[0] > 0) {
        StringBuilder allSellSb = new StringBuilder();
        Map<EconomyUse, BigDecimal> totals = new LinkedHashMap<>();
        for (Map.Entry<EconomyUse, BigDecimal> entry : sellPerUnit.entrySet()) {
          BigDecimal total = entry.getValue().multiply(BigDecimal.valueOf(sold[0]));
          EconomyApi.addMoney(player.getUuid(), total, entry.getKey());
          allSellSb.append(EconomyApi.formatMoney(total, entry.getKey())).append(" ");
          totals.put(entry.getKey(), total);
        }

        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageSimpleSell()
            .replace("%product%", productTemplate.getName().getString())
            .replace("%amount%", String.valueOf(sold[0]))
            .replace("%price%", allSellSb.toString().trim()),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);

        saveTransactions(player, product, shop, sold[0], totals, ActionShop.SELL, config, ctx);
      }
    }
  }

  /**
   * Sell all matching items from the player's inventory using the pre-built sell index.
   */
  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks) {
    if (itemStacks.isEmpty()) return;
    if (sellLock.containsKey(player.getUuid())) return;

    sellLock.put(player.getUuid(), System.currentTimeMillis());
    ShopContext ctx = ShopContext.get();

    ctx.runOnServer(() -> {
      try {
        long start = System.currentTimeMillis();
        SellProductIndex index = ctx.getSellIndex();
        ShopConfig config = ctx.getMainConfig();
        Map<EconomyUse, BigDecimal> earnings = new LinkedHashMap<>();
        List<SellAction> actions = collectSellActions(player, itemStacks, index, config, earnings);

        if (actions.isEmpty()) {
          PlayerUtils.sendMessage(player, ctx.getLang().getMessageNotSell(),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }

        // Decrement sold items synchronously on the server main thread
        for (SellAction action : actions) {
          action.itemStack.decrement(action.amount);
        }

        StringBuilder allSell = rewardSellAll(player, ctx, earnings);

        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageSell().replace("%sell%", allSell.toString()),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);

        saveSellAllTransactions(player, actions, config, ctx);
        logSellAllTiming(config, start);
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error in sellAll: " + e.getMessage());
      } finally {
        sellLock.remove(player.getUuid());
      }
    });
  }

  public static void removeSellLock(UUID uuid) {
    sellLock.remove(uuid);
  }

  private static ItemChance buildItemChance(Product product) {
    return ItemChance.builder()
      .item(product.getProduct())
      .chance(0D)
      .build();
  }

  private static boolean hasInventorySpace(ServerPlayerEntity player, Product product, int amount,
                                           ItemStack itemStack, ShopContext ctx) {
    if (product.getProduct().startsWith("command:") || product.getProduct().startsWith("pokemon:")
      || product.getProduct().contains("|")) {
      return true;
    }
    int maxStack = itemStack.getMaxCount();
    int slotsNeeded = (int) Math.ceil((double) amount / maxStack);
    long emptySlots = player.getInventory().main.stream().filter(ItemStack::isEmpty).count();
    if (emptySlots >= slotsNeeded) {
      return true;
    }
    PlayerUtils.sendMessage(player,
      ctx.getLang().getMessageNotEnoughSpace()
        .replace(PLACEHOLDER_AMOUNT, String.valueOf(amount))
        .replace("%slots%", String.valueOf(slotsNeeded)),
      ctx.getLang().getPrefix(), TypeMessage.CHAT);
    return false;
  }

  private static boolean canAffordBuy(ServerPlayerEntity player, ItemChance itemChance, ItemStack itemStack,
                                      int amount, Map<EconomyUse, BigDecimal> buyPrices, ShopContext ctx) {
    for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
      if (!EconomyApi.hasEnoughMoney(player.getUuid(), entry.getValue(), entry.getKey(), false)) {
        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageNotEnoughMoney()
            .replace("%product%", itemChance.getTitle())
            .replace(PLACEHOLDER_AMOUNT, String.valueOf(amount))
            .replace("%pack%", String.valueOf(itemStack.getCount()))
            .replace(PLACEHOLDER_PRICE, EconomyApi.formatMoney(entry.getValue(), entry.getKey())),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);
        return false;
      }
    }
    return true;
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

  private static void chargeBuyPrices(ServerPlayerEntity player, Map<EconomyUse, BigDecimal> buyPrices) {
    for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
      EconomyApi.removeMoney(player.getUuid(), entry.getValue(), entry.getKey());
    }
  }

  private static void refundBuyPrices(ServerPlayerEntity player, Map<EconomyUse, BigDecimal> buyPrices) {
    for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
      EconomyApi.addMoney(player.getUuid(), entry.getValue(), entry.getKey());
    }
  }

  private static void releaseStock(ServerPlayerEntity player, Product product, int amount, ShopContext ctx) {
    if (!product.hasStockControl() || product.getStockAmount() == null) {
      return;
    }
    ctx.getAsyncContext().runAsync(() -> {
      ctx.getRepositories().getStockRepository().release(
        player.getUuid(),
        product.getUuid(),
        product.getStockMode(),
        amount
      );
    });
  }

  private static void persistProductLimit(ServerPlayerEntity player, Product product, int amount, ShopContext ctx) {
    if (product.getUuid() == null) {
      return;
    }
    ctx.getAsyncContext().runAsync(() -> {
      UserInfo userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
      if (userInfo == null) {
        userInfo = new UserInfo(player.getUuid(), player.getGameProfile().getName());
      }
      userInfo.addProductLimit(product, amount);
      ctx.getRepositories().getUserRepository().save(userInfo);
    });
  }

  private static void saveTransactions(ServerPlayerEntity player, Product product, ShopReference shop, int amount,
                                       Map<EconomyUse, BigDecimal> totals, ActionShop action, ShopConfig config,
                                       ShopContext ctx) {
    if (config == null || !config.isSaveTransactions()) {
      return;
    }
    ctx.getAsyncContext().runAsync(() -> {
      for (Map.Entry<EconomyUse, BigDecimal> entry : totals.entrySet()) {
        ctx.getRepositories().getTransactionRepository().save(Transaction.builder()
          .playerUuid(player.getUuid())
          .playerName(player.getGameProfile().getName())
          .shopId(shop.getId())
          .productId(product.getProduct())
          .action(action)
          .amount(amount)
          .value(entry.getValue())
          .currency(entry.getKey().getCurrency())
          .timestamp(System.currentTimeMillis())
          .build());
      }
    });
  }

  private static List<SellAction> collectSellActions(ServerPlayerEntity player, List<ItemStack> itemStacks,
                                                     SellProductIndex index, ShopConfig config,
                                                     Map<EconomyUse, BigDecimal> earnings) {
    List<SellAction> actions = new ArrayList<>();
    for (ItemStack itemStack : itemStacks) {
      if (!itemStack.isEmpty()) {
        SellAction action = findSellAction(player, itemStack, index, config, earnings);
        if (action != null) {
          actions.add(action);
        }
      }
    }
    return actions;
  }

  private static SellAction findSellAction(ServerPlayerEntity player, ItemStack itemStack, SellProductIndex index,
                                           ShopConfig config, Map<EconomyUse, BigDecimal> earnings) {
    List<SellProductIndex.SellEntry> entries = index.findSellable(itemStack, player);
    for (SellProductIndex.SellEntry entry : entries) {
      if (PriceCalculator.canSell(entry.product(), player, entry.shop(), config)) {
        Map<EconomyUse, BigDecimal> perUnit = PriceCalculator.getSellPricesPerUnit(entry.product(), entry.shop());
        if (!perUnit.isEmpty()) {
          return createSellAction(itemStack, entry.shop(), entry.product(), perUnit, earnings);
        }
      }
    }
    return null;
  }

  private static SellAction createSellAction(ItemStack itemStack, Shop shop, Product product,
                                             Map<EconomyUse, BigDecimal> perUnit,
                                             Map<EconomyUse, BigDecimal> earnings) {
    int count = itemStack.getCount();
    Map<EconomyUse, BigDecimal> totals = new LinkedHashMap<>();
    for (Map.Entry<EconomyUse, BigDecimal> entry : perUnit.entrySet()) {
      BigDecimal total = entry.getValue().multiply(BigDecimal.valueOf(count));
      totals.put(entry.getKey(), total);
      earnings.merge(entry.getKey(), total, BigDecimal::add);
    }
    return new SellAction(itemStack, shop, product, count, totals);
  }



  private static StringBuilder rewardSellAll(ServerPlayerEntity player, ShopContext ctx,
                                             Map<EconomyUse, BigDecimal> earnings) {
    StringBuilder allSell = new StringBuilder();
    earnings.forEach((economy, price) -> {
      allSell.append(ctx.getLang().getFormatSell()
          .replace(PLACEHOLDER_PRICE, EconomyApi.formatMoney(price, economy)))
        .append("\n");
      EconomyApi.addMoney(player.getUuid(), price, economy);
    });
    return allSell;
  }

  private static void saveSellAllTransactions(ServerPlayerEntity player, List<SellAction> actions,
                                              ShopConfig config, ShopContext ctx) {
    if (config == null || !config.isSaveTransactions()) {
      return;
    }
    for (SellAction action : actions) {
      saveTransactions(player, action.product(), action.shop(), action.amount(), action.totals(), ActionShop.SELL, config, ctx);
    }
  }

  private static void logSellAllTiming(ShopConfig config, long start) {
    if (config != null && config.isDebug()) {
      UltraShop.LOGGER.info("SellAll took " + (System.currentTimeMillis() - start) + "ms");
    }
  }

  private record SellAction(ItemStack itemStack, Shop shop, Product product, int amount,
                            Map<EconomyUse, BigDecimal> totals) {
  }
}

