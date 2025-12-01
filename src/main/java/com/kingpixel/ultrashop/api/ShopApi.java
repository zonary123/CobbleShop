package com.kingpixel.ultrashop.api;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.command.CommandTree;
import com.kingpixel.ultrashop.config.Config;
import com.kingpixel.ultrashop.database.DataBaseFactory;
import com.kingpixel.ultrashop.migrate.OldShop;
import com.kingpixel.ultrashop.models.ActionShop;
import com.kingpixel.ultrashop.models.Product;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.SubShop;
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
    UltraShop.initSellProduct(options);
    Config main = configs.get(UltraShop.MOD_ID);
    if (main == null) return;
    UltraShop.lang.init(main);
  }


  public static List<Shop> getShops(ShopOptionsApi options) {
    return shops.get(options.getModId());
  }

  public static List<Shop> getShops(List<SubShop> subShops) {
    return shops.get(UltraShop.MOD_ID).stream().filter(shop -> subShops.stream().anyMatch(subShop -> subShop.getIdShop().equals(shop.getId()))).toList();
  }

  public static Shop getShop(ShopOptionsApi options, String id) {
    var s = shops.get(options.getModId());
    for (Shop shop : s) {
      if (shop.getId().equals(id))
        return shop;
    }
    return null;
  }

  public static Config getConfig(ShopOptionsApi options) {
    return configs.get(options.getModId());
  }

  public static final Map<UUID, Long> sellLock = new HashMap<>();

  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks, ShopOptionsApi options) {
    CompletableFuture.runAsync(() -> {
        if (itemStacks.isEmpty()) return;
        if (sellLock.containsKey(player.getUuid())) return;
        sellLock.put(player.getUuid(), System.currentTimeMillis());
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
            allSell.append(UltraShop.lang.getFormatSell()
                .replace("%price%", EconomyApi.formatMoney(price, economyUse)))
              .append("\n");
            EconomyApi.addMoney(player.getUuid(), price, economyUse);
          });
          PlayerUtils.sendMessage(player, UltraShop.lang.getMessageSell().replace("%sell%", allSell.toString()), UltraShop.lang.getPrefix(), TypeMessage.CHAT);
        } else {
          PlayerUtils.sendMessage(player, UltraShop.lang.getMessageNotSell(), UltraShop.lang.getPrefix(), TypeMessage.CHAT);
        }

        if (ShopApi.getMainConfig().isDebug()) {
          long duration = System.currentTimeMillis() - start;
          CobbleUtils.LOGGER.info(UltraShop.MOD_ID, "Sell took " + duration + "ms");
        }
        sellLock.remove(player.getUuid());
      }, UltraShop.SHOP_EXECUTOR)
      .exceptionally(e -> {
        CobbleUtils.LOGGER.error(UltraShop.MOD_ID, "Error selling items -> " + e);
        sellLock.remove(player.getUuid());
        return null;
      });
  }

  public static Config getMainConfig() {
    return getConfig(ShopOptionsApi.builder()
      .modId(UltraShop.MOD_ID).build());
  }


}
