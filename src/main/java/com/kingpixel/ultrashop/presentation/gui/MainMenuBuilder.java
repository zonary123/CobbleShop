package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Sound;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.UIUtils;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and opens the main shop listing menu.
 * Extracted from Config.open().
 */
public final class MainMenuBuilder {

  private MainMenuBuilder() {
  }

  /**
   * Opens the main shop menu for a player using the default mod config.
   */
  public static void open(ServerPlayerEntity player, ShopConfig config) {
    open(player, config, UltraShop.MOD_ID);
  }

  /**
   * Opens the main shop menu for a specific mod's shops.
   */
  public static void open(ServerPlayerEntity player, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();

    ChestTemplate template = ChestTemplate.builder(config.getRows()).build();
    PanelsConfig.applyConfig(template, config.getPanels(), config.getRows());

    List<Shop> shops = ctx.getShops(modId);
    NavigationContext nav = new NavigationContext();

    for (Shop shop : shops) {
      if (UIUtils.isInside(shop.getDisplay().getSlot(), config.getRows())) {
        ItemModel display = LangConfig.resolve(shop.getDisplay(), lang.getGlobalDisplay());
        List<String> lore = new ArrayList<>(display.getLore());
        GooeyButton button = display.getButton(1,
          display.getDisplayname().replace("%shop%", shop.getId()),
          lore,
          action -> ShopMenuBuilder.navigateTo(player, shop, nav, config, true));
        template.set(shop.getDisplay().getSlot(), button);
      }
    }

    // Close button
    if (UIUtils.isInside(config.getItemClose().getSlot(), config.getRows())) {
      ItemModel close = LangConfig.resolve(config.getItemClose(), lang.getGlobalItemClose());
      GooeyButton closeButton = close.getButton(1, action -> UIManager.closeUI(player));
      template.set(config.getItemClose().getSlot(), closeButton);
    }

    GooeyPage page = GooeyPage.builder()
      .template(template)
      .title(AdventureTranslator.toNative(config.getTitle()))
      .onOpen(action -> new Sound(config.getSoundOpen()).playSoundPlayer(action.getPlayer()))
      .build();

    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }
}

