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
    new Config().readConfig(options);
    options.setCommands(configs.get(options.getModId()).getCommands());
    Config.readShops(options);
    CobbleShop.lang.init(configs.get(CobbleShop.MOD_ID));
    CommandTree.register(options, dispatcher);
    CobbleShop.initSellProduct();
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

  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks) {
    Map<String, BigDecimal> dataSell = new HashMap<>();
    for (ItemStack itemStack : itemStacks) {
      sellProducts.forEach((shop, products) -> products.forEach(product -> {
        Product.SellProduct sellProduct = Product.sellProduct(shop, itemStack, product);
        if (sellProduct == null) return;
        int amount = itemStack.getCount();
        DataBaseFactory.INSTANCE.addTransaction(player, shop, product, ActionShop.SELL, amount,
          product.getSellPrice(amount));
        dataSell.put(sellProduct.getCurrency(), dataSell.getOrDefault(sellProduct.getCurrency(), BigDecimal.ZERO).add(sellProduct.getPrice()));
      }));
    }
    if (!dataSell.isEmpty()) {
      String message = CobbleShop.lang.getMessageSell();
      StringBuilder allSell = new StringBuilder();
      dataSell.forEach((currency, price) -> {
        allSell.append(CobbleShop.lang.getFormatSell().replace("%price%", EconomyApi.formatMoney(price, currency))).append("\n");
        EconomyApi.addMoney(player, price, currency);
      });

      PlayerUtils.sendMessage(
        player,
        message.replace("%sell%", allSell.toString()),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
    } else {
      PlayerUtils.sendMessage(
        player,
        CobbleShop.lang.getMessageNotSell(),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
    }
  }

  public static Config getMainConfig() {
    return getConfig(ShopOptionsApi.builder()
      .modId(CobbleShop.MOD_ID).build());
  }


}
