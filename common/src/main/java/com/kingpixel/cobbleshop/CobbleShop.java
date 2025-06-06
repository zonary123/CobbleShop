package com.kingpixel.cobbleshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kingpixel.cobbleshop.adapters.*;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Lang;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleshop.models.DataShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.Utils;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:05
 */
public class CobbleShop {

  public static final String MOD_ID = "cobbleshop";
  public static final String MOD_NAME = "CobbleShop";
  public static final String PATH = "/config/cobbleshop/";
  public static final String PATH_SHOP = PATH + "shop/";
  public static final String PATH_LANG = PATH + "lang/";
  public static final String PATH_MIGRATION = PATH + "migration/";
  public static final String PATH_BACKUP_MIGRATION = PATH + "backup_migration/";
  public static final String PATH_DATA = PATH + "data/";
  public static final String PATH_DATA_USERS = PATH_DATA + "users/";
  public static MinecraftServer server;
  public static ShopOptionsApi options;
  public static Lang lang = new Lang();
  public static Gson gson;
  public static Gson gsonWithOutSpaces;
  public static DataShop dataShop = new DataShop();


  private static GsonBuilder addAdapters(GsonBuilder gsonBuilder) {
    return gsonBuilder.registerTypeAdapter(ShopType.class, ShopTypeAdapter.INSTANCE)
      .registerTypeAdapter(ShopTypePermanent.class, ShopTypePermanent.INSTANCE)
      .registerTypeAdapter(ShopTypeDynamic.class, ShopTypeDynamic.INSTANCE)
      .registerTypeAdapter(ShopTypeWeekly.class, ShopTypeWeekly.INSTANCE)
      .registerTypeAdapter(ShopTypeDynamicWeekly.class, ShopTypeDynamicWeekly.INSTANCE)
      .registerTypeAdapter(ShopTypeCalendar.class, ShopTypeCalendar.INSTANCE)
      .registerTypeAdapter(ShopTypeDynamicCalendar.class, ShopTypeDynamicCalendar.INSTANCE);
  }

  public static void init() {
    gson = addAdapters(Utils.newGson().newBuilder()).create();
    gsonWithOutSpaces = addAdapters(Utils.newWithoutSpacingGson().newBuilder()).create();
    options = ShopOptionsApi.builder()
      .modId(MOD_ID)
      .path(PATH)
      .build();
    events();
  }

  public static void load(ShopOptionsApi options) {
    ShopApi.register(options, server.getCommandManager().getDispatcher());
    dataShop.init();
    new DataBaseFactory(ShopApi.getMainConfig().getDataBase());
  }

  public static void events() {


    LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
      server = level.getServer();
      var source = server.getCommandSource();
      ShopApi.shops.forEach((modId, list) -> {
        list.forEach(shop -> {
          PermissionApi.hasPermission(source, shop.getPermission(ShopOptionsApi.builder()
            .modId(modId).build()), 4);
        });
      });
    });


    CommandRegistrationEvent.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
      ShopApi.register(options, dispatcher);
      dataShop.init();
      new DataBaseFactory(ShopApi.getMainConfig().getDataBase());
    });

    PlayerEvent.PLAYER_JOIN.register(player -> DataBaseFactory.INSTANCE.getUserInfo(player));
    PlayerEvent.PLAYER_QUIT.register(player -> {
      DataBaseFactory.INSTANCE.removeIfNecessary(player);
      ShopApi.sellLock.remove(player.getUuid());
    });
  }

  public static void initSellProduct(ShopOptionsApi options) {
    Map<Shop, List<Product>> products = new HashMap<>();

    ShopApi.shops.forEach((modId, shops) -> {
      shops.forEach(shop -> {
        List<Product> productsShop = new ArrayList<>();
        shop.getType().getProducts(shop, ShopOptionsApi.builder()
          .modId(modId)
          .build()).forEach(product -> {
          if (product.canSell(null, shop, options)) {
            productsShop.add(product);
          }
        });
        products.put(shop, productsShop);
      });
    });

    ShopApi.sellProducts = products;
  }
}
