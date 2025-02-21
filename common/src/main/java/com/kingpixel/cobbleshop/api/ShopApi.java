package com.kingpixel.cobbleshop.api;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.command.CommandTree;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.models.Shop;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;

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

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    new Config().readConfig(options);
    options.setCommands(configs.get(options.getModId()).getCommands());
    Config.readShops(options);
    CobbleShop.lang.init(configs.get(CobbleShop.MOD_ID));
    CommandTree.register(options, dispatcher);
  }

  public static int getCooldown(Shop shop) {
    return 0;
  }

  public static List<Shop> getShops(ShopOptionsApi options) {
    return shops.get(options.getModId());
  }

  public static Shop getShop(ShopOptionsApi options, String id) {
    return shops.get(options.getModId()).stream().filter(shop -> shop.getId().equals(id)).findFirst().orElse(null);
  }

  public static Config getConfig(ShopOptionsApi options) {
    return configs.get(options.getModId());
  }
}
