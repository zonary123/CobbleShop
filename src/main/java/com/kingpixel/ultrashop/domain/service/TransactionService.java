package com.kingpixel.ultrashop.domain.service;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.*;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.infrastructure.index.SellProductIndex;
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

  private static final Map<UUID, Long> sellLock = new ConcurrentHashMap<>();

  private TransactionService() {
  }

  /**
   * Buy a product for a player. Charges ALL economies in the product's effective prices.
   */
  public static boolean buy(ServerPlayerEntity player, Product product, Shop shop, int amount,
                            ShopConfig config) {
    ShopContext ctx = ShopContext.get();
    synchronized (ctx.getTransactionLock(player.getUuid())) {
      ItemChance itemChance = new ItemChance(product.getProduct(), 0);
      ItemStack itemStack = itemChance.getItemStack();

      // Check inventory space (only for physical items)
      if (!product.getProduct().startsWith("command:") && !product.getProduct().startsWith("pokemon:")
        && !product.getProduct().contains("|")) {
        int maxStack = itemStack.getMaxCount();
        int slotsNeeded = (int) Math.ceil((double) amount / maxStack);
        long emptySlots = player.getInventory().main.stream().filter(ItemStack::isEmpty).count();
        if (emptySlots < slotsNeeded) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageNotEnoughSpace()
              .replace("%amount%", String.valueOf(amount))
              .replace("%slots%", String.valueOf(slotsNeeded)),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return false;
        }
      }

      Map<EconomyUse, BigDecimal> buyPrices = PriceCalculator.getBuyPrices(product, player, amount, shop, config);

      for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
        if (!EconomyApi.hasEnoughMoney(player.getUuid(), entry.getValue(), entry.getKey(), false)) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageNotEnoughMoney()
              .replace("%product%", itemChance.getTitle())
              .replace("%amount%", String.valueOf(amount))
              .replace("%pack%", String.valueOf(itemStack.getCount()))
              .replace("%price%", EconomyApi.formatMoney(entry.getValue(), entry.getKey())),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return false;
        }
      }

      for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
        EconomyApi.removeMoney(player.getUuid(), entry.getValue(), entry.getKey());
      }

      ctx.runOnServer(() -> ItemChance.giveReward(player, itemChance, amount));

      if (product.getUuid() != null) {
        UserInfo userInfo = ctx.getRepositories().getUserRepository().findByUuid(player.getUuid());
        if (userInfo != null) {
          userInfo.addProductLimit(product, amount);
          ctx.getRepositories().getUserRepository().save(userInfo);
        }
      }

      if (config.isSaveTransactions()) {
        for (Map.Entry<EconomyUse, BigDecimal> entry : buyPrices.entrySet()) {
          ctx.getRepositories().getTransactionRepository().save(Transaction.builder()
            .playerUuid(player.getUuid())
            .playerName(player.getGameProfile().getName())
            .shopId(shop.getId())
            .productId(product.getProduct())
            .action(ActionShop.BUY)
            .amount(amount)
            .value(entry.getValue())
            .currency(entry.getKey().getCurrency())
            .timestamp(System.currentTimeMillis())
            .build());
        }
      }

      return true;
    }
  }

  /**
   * Sell a specific product from a player's inventory.
   */
  public static void sell(ServerPlayerEntity player, Product product, Shop shop, int amount,
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

      var inventory = player.getInventory();
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
        for (Map.Entry<EconomyUse, BigDecimal> entry : sellPerUnit.entrySet()) {
          BigDecimal total = entry.getValue().multiply(BigDecimal.valueOf(sold[0]));
          EconomyApi.addMoney(player.getUuid(), total, entry.getKey());
          allSellSb.append(EconomyApi.formatMoney(total, entry.getKey())).append(" ");
        }

        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageSimpleSell()
            .replace("%product%", productTemplate.getName().getString())
            .replace("%amount%", String.valueOf(sold[0]))
            .replace("%price%", allSellSb.toString().trim()),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);

        if (config.isSaveTransactions()) {
          for (Map.Entry<EconomyUse, BigDecimal> entry : sellPerUnit.entrySet()) {
            BigDecimal total = entry.getValue().multiply(BigDecimal.valueOf(sold[0]));
            ctx.getRepositories().getTransactionRepository().save(Transaction.builder()
              .playerUuid(player.getUuid())
              .playerName(player.getGameProfile().getName())
              .shopId(shop.getId())
              .productId(product.getProduct())
              .action(ActionShop.SELL)
              .amount(sold[0])
              .value(total)
              .currency(entry.getKey().getCurrency())
              .timestamp(System.currentTimeMillis())
              .build());
          }
        }
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

    ctx.getAsyncContext().runAsync(() -> {
      try {
        long start = System.currentTimeMillis();
        SellProductIndex index = ctx.getSellIndex();
        ShopConfig config = ctx.getMainConfig();
        Map<EconomyUse, BigDecimal> earnings = new LinkedHashMap<>();

        List<SellAction> actions = new ArrayList<>();
        for (ItemStack itemStack : itemStacks) {
          if (itemStack.isEmpty()) continue;
          var entries = index.findSellable(itemStack, player);
          for (var entry : entries) {
            // Safety: skip products where sell > buy (exploit prevention)
            if (!PriceCalculator.canSell(entry.product(), player, entry.shop(), config)) continue;

            Map<EconomyUse, BigDecimal> perUnit = PriceCalculator.getSellPricesPerUnit(entry.product(), entry.shop());
            if (perUnit.isEmpty()) continue;

            int count = itemStack.getCount();
            Map<EconomyUse, BigDecimal> totals = new LinkedHashMap<>();
            for (Map.Entry<EconomyUse, BigDecimal> e : perUnit.entrySet()) {
              BigDecimal total = e.getValue().multiply(BigDecimal.valueOf(count));
              totals.put(e.getKey(), total);
              earnings.merge(e.getKey(), total, BigDecimal::add);
            }
            actions.add(new SellAction(itemStack, entry.shop(), entry.product(), count, totals));
            break;
          }
        }

        if (actions.isEmpty()) {
          PlayerUtils.sendMessage(player, ctx.getLang().getMessageNotSell(),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }

        ctx.runOnServer(() -> {
          for (SellAction action : actions) {
            action.itemStack.decrement(action.amount);
          }
        });

        StringBuilder allSell = new StringBuilder();
        earnings.forEach((economy, price) -> {
          allSell.append(ctx.getLang().getFormatSell()
              .replace("%price%", EconomyApi.formatMoney(price, economy)))
            .append("\n");
          EconomyApi.addMoney(player.getUuid(), price, economy);
        });

        PlayerUtils.sendMessage(player,
          ctx.getLang().getMessageSell().replace("%sell%", allSell.toString()),
          ctx.getLang().getPrefix(), TypeMessage.CHAT);

        if (config != null && config.isSaveTransactions()) {
          for (SellAction action : actions) {
            for (Map.Entry<EconomyUse, BigDecimal> e : action.totals.entrySet()) {
              ctx.getRepositories().getTransactionRepository().save(Transaction.builder()
                .playerUuid(player.getUuid())
                .playerName(player.getGameProfile().getName())
                .shopId(action.shop().getId())
                .productId(action.product().getProduct())
                .action(ActionShop.SELL)
                .amount(action.amount())
                .value(e.getValue())
                .currency(e.getKey().getCurrency())
                .timestamp(System.currentTimeMillis())
                .build());
            }
          }
        }

        if (config != null && config.isDebug()) {
          UltraShop.LOGGER.info(UltraShop.MOD_ID, "SellAll took " + (System.currentTimeMillis() - start) + "ms");
        }
      } catch (Exception e) {
        UltraShop.LOGGER.error(UltraShop.MOD_ID, "Error in sellAll: " + e.getMessage());
      } finally {
        sellLock.remove(player.getUuid());
      }
    });
  }

  public static void removeSellLock(UUID uuid) {
    sellLock.remove(uuid);
  }

  private record SellAction(ItemStack itemStack, Shop shop, Product product, int amount,
                            Map<EconomyUse, BigDecimal> totals) {
  }
}

