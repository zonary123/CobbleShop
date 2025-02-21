package com.kingpixel.cobbleshop;

import com.google.gson.Gson;
import com.kingpixel.cobbleshop.adapters.*;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Lang;
import com.kingpixel.cobbleutils.util.Utils;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import net.minecraft.server.MinecraftServer;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:05
 */
public class CobbleShop {

  public static final String MOD_ID = "cobbleshop";
  public static final String MOD_NAME = "CobbleShop";
  public static final String PATH = "/config/cobbleshop/";
  public static final String PATH_SHOP = PATH + "shop/";
  public static final String PATH_LANG = PATH + "lang/";
  public static final String PATH_DATA = PATH + "data/";
  public static MinecraftServer server;
  public static ShopOptionsApi options;
  public static Lang lang = new Lang();
  public static Gson gson;

  public static void init() {
    gson = Utils.newGson().newBuilder()
      .registerTypeAdapter(ShopType.class, ShopTypeAdapter.INSTANCE)
      .registerTypeAdapter(ShopTypePermanent.class, ShopTypePermanent.INSTANCE)
      .registerTypeAdapter(ShopTypeDynamic.class, ShopTypeDynamic.INSTANCE)
      .registerTypeAdapter(ShopTypeWeekly.class, ShopTypeWeekly.INSTANCE)
      .registerTypeAdapter(ShopTypeDynamicWeekly.class, ShopTypeDynamicWeekly.INSTANCE)
      .create();
    options = ShopOptionsApi.builder()
      .modId(MOD_ID)
      .path(PATH)
      .build();
    events();
  }

  public static void load() {
    ShopApi.register(options, server.getCommandManager().getDispatcher());
  }

  public static void events() {

    LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> server = level.getServer());

    CommandRegistrationEvent.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
      ShopApi.register(options, dispatcher);
    });

  }
}
