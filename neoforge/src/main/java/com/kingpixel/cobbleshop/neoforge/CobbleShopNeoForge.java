package com.kingpixel.cobbleshop.neoforge;

import com.kingpixel.cobbleshop.CobbleShop;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CobbleShop.MOD_ID)
public class CobbleShopNeoForge {

  public CobbleShopNeoForge(IEventBus modBus) {
    CobbleShop.init();
  }
}
