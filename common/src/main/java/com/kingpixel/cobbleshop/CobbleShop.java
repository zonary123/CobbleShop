package com.kingpixel.cobbleshop;

import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Lang;
import dev.architectury.event.events.common.CommandRegistrationEvent;

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
  public static Lang lang = new Lang();

  public static void init() {
    events();
  }

  public static void load() {
    lang.init();
  }

  public static void events() {

    CommandRegistrationEvent.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
      ShopApi.register(ShopOptionsApi.builder()
        .modId(MOD_ID)
        .path(PATH)
        .build(), dispatcher);
    });

  }
}
