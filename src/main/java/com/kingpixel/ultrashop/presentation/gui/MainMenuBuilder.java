package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Sound;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.LuckPermsUtil;
import com.kingpixel.cobbleutils.util.UIUtils;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.server.network.ServerPlayerEntity;
 
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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

    List<Shop> shops = visibleInMainMenu(ctx.getTypedShops(modId));
    NavigationContext nav = new NavigationContext();

    for (Shop shop : shops) {
      ItemModel displayItem = shop.getDisplayConfig() != null
          ? shop.getDisplayConfig().getDisplayItem()
          : null;
      if (displayItem == null) {
        continue;
      }
      if (UIUtils.isInside(displayItem.getSlot(), config.getRows())) {
        ItemModel display = LangConfig.resolve(displayItem, lang.getGlobalDisplay());
        List<String> lore = new ArrayList<>(display.getLore());
        GooeyButton button = getButton(display,
            display.getDisplayname().replace("%shop%", shop.getId()),
            lore,
            action -> ShopMenuBuilder.navigateTo(player, shop, nav, config, true));
        template.set(displayItem.getSlot(), button);
      }
    }

    // Close button
    if (UIUtils.isInside(config.getItemClose().getSlot(), config.getRows())) {
      ItemModel close = LangConfig.resolve(config.getItemClose(), lang.getGlobalItemClose());
      GooeyButton closeButton = getButton(close, action -> UIManager.closeUI(player));
      template.set(config.getItemClose().getSlot(), closeButton);
    }

    GooeyPage page = GooeyPage.builder()
        .template(template)
        .title(AdventureTranslator.toNative(config.getTitle()))
        .onOpen(action -> new Sound(config.getSoundOpen()).playSoundPlayer(action.getPlayer()))
        .build();

    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));

  }

  /**
   * Shops shown in main menu:
   * <ul>
   * <li>shops not referenced by any category sub-entry</li>
   * <li>category container shops remain visible unless they are themselves
   * nested</li>
   * </ul>
   */
  static List<Shop> visibleInMainMenu(List<Shop> shops) {
    Set<String> nestedShopIds = referencedSubShopIds(shops);
    return shops.stream()
        .filter(shop -> !nestedShopIds.contains(shop.getId()))
        .toList();
  }

  private static Set<String> referencedSubShopIds(List<Shop> shops) {
    Set<String> result = new LinkedHashSet<>();
    for (Shop shop : shops) {
      if (shop instanceof CategoryShop categoryShop && categoryShop.getSubShops() != null) {
        for (SubShop subShop : categoryShop.getSubShops()) {
          if (subShop != null && subShop.getIdShop() != null && !subShop.getIdShop().isBlank()) {
            result.add(subShop.getIdShop());
          }
        }
      }
    }
    return result;
  }

  private static GooeyButton getButton(ItemModel model,
      Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
        .display(model.getItemStack())
        .onClick(onClick::accept)
        .build();
  }

  private static GooeyButton getButton(ItemModel model, String title, List<String> lore,
      Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
        .display(model.getItemStack())
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(title))
        .with(DataComponentTypes.LORE,
            new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .onClick(onClick::accept)
        .build();
  }
}
