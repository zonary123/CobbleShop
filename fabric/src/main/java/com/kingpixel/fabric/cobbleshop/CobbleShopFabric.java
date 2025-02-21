package com.kingpixel.fabric.cobbleshop;

import com.kingpixel.cobbleshop.CobbleShop;
import net.fabricmc.api.ModInitializer;

public class CobbleShopFabric implements ModInitializer {

  @Override
  public void onInitialize() {
    CobbleShop.init();
  }

}
