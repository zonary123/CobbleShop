package com.kingpixel.cobbleshop.api;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.command.CommandTree;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleshop.migrate.OldShop;
import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.SubShop;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 28/09/2024 20:15
 */
public class ShopApi {
  // ModId -> Config
  public static Map<String, Config> configs = new HashMap<>();
  // ModId -> List<Shop>
  public static Map<String, List<Shop>> shops = new HashMap<>();
  public static Map<Shop, List<Product>> sellProducts = new HashMap<>();

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    OldShop.migration();
    Config config = new Config().readConfig(options);
    configs.put(options.getModId(), config);
    options.setCommands(config.getCommands());
    Config.readShops(options);
    CommandTree.register(options, dispatcher);
    CobbleShop.initSellProduct(options);
    Config main = configs.get(CobbleShop.MOD_ID);
    if (main == null) return;
    CobbleShop.lang.init(main);
  }


  public static List<Shop> getShops(ShopOptionsApi options) {
    return shops.get(options.getModId());
  }

  public static List<Shop> getShops(List<SubShop> subShops) {
    return shops.get(CobbleShop.MOD_ID).stream().filter(shop -> subShops.stream().anyMatch(subShop -> subShop.getIdShop().equals(shop.getId()))).toList();
  }

  public static Shop getShop(ShopOptionsApi options, String id) {
    return shops.get(options.getModId()).stream().filter(shop -> shop.getId().equals(id)).findFirst().orElse(null);
  }

  public static Config getConfig(ShopOptionsApi options) {
    return configs.get(options.getModId());
  }

  public static final Map<UUID, Long> sellLock = new HashMap<>();

  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks, ShopOptionsApi options) {
    if (itemStacks.isEmpty()) return;
    if (sellLock.containsKey(player.getUuid())) return;
    sellLock.put(player.getUuid(), System.currentTimeMillis());
    CompletableFuture.runAsync(() -> {
        long start = System.currentTimeMillis();
        Map<EconomyUse, BigDecimal> dataSell = itemStacks.stream()
          .flatMap(itemStack -> sellProducts.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
              .filter(product -> product.canSell(player, entry.getKey(), options))
              .map(product -> {
                BigDecimal sellPrice = Product.sellProduct(entry.getKey(), itemStack, product);
                if (sellPrice.compareTo(BigDecimal.ZERO) > 0) {
                  int amount = itemStack.getCount();
                  DataBaseFactory.INSTANCE.addTransaction(player, entry.getKey(), product, ActionShop.SELL, amount, product.getSellPrice(amount));
                }
                return Map.entry(entry.getKey().getEconomy(), sellPrice);
              })
              .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
            )
          )
          .collect(HashMap::new, (map, entry) -> map.merge(entry.getKey(), entry.getValue(), BigDecimal::add), HashMap::putAll);

        if (!dataSell.isEmpty()) {
          StringBuilder allSell = new StringBuilder();
          dataSell.forEach((economyUse, price) -> {
            allSell.append(CobbleShop.lang.getFormatSell()
                .replace("%price%", EconomyApi.formatMoney(price, economyUse)))
              .append("\n");
            EconomyApi.addMoney(player.getUuid(), price, economyUse);
          });
          PlayerUtils.sendMessage(player, CobbleShop.lang.getMessageSell().replace("%sell%", allSell.toString()), CobbleShop.lang.getPrefix(), TypeMessage.CHAT);
        } else {
          PlayerUtils.sendMessage(player, CobbleShop.lang.getMessageNotSell(), CobbleShop.lang.getPrefix(), TypeMessage.CHAT);
        }

        if (ShopApi.getMainConfig().isDebug()) {
          long duration = System.currentTimeMillis() - start;
          CobbleUtils.LOGGER.info(CobbleShop.MOD_ID, "Sell took " + duration + "ms");
        }
        sellLock.remove(player.getUuid());
      }, CobbleShop.SHOP_EXECUTOR)
      .exceptionally(e -> {
        CobbleUtils.LOGGER.error(CobbleShop.MOD_ID, "Error selling items -> " + e);
        sellLock.remove(player.getUuid());
        return null;
      });
  }

  public static Config getMainConfig() {
    return getConfig(ShopOptionsApi.builder()
      .modId(CobbleShop.MOD_ID).build());
  }


}
