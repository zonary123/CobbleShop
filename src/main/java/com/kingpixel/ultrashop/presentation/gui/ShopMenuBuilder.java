package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.*;
import com.kingpixel.cobbleutils.Model.conditions.util.ConditionUtils;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.*;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds and opens a shop menu (products or categories).
 * Extracted from Shop.open() — presentation only.
 */
public final class ShopMenuBuilder {


  private ShopMenuBuilder() {
  }

  /**
   * Opens the current shop in the navigation context.
   */
  public static void open(ServerPlayerEntity player, NavigationContext nav, ShopConfig config, boolean withClose) {
    Shop shop = nav.current();
    if (shop == null) {
      MainMenuBuilder.open(player, config);
      return;
    }
    openShop(player, shop, nav, config, withClose);
  }

  /**
   * Opens a specific shop, pushing it to navigation if needed.
   */
  public static void openShop(ServerPlayerEntity player, Shop shop, NavigationContext nav,
                              ShopConfig config, boolean withClose) {
    ShopContext ctx = ShopContext.get();

    ctx.getAsyncContext().runAsync(() -> {
      try {
        String modId = ctx.getConfigs().entrySet().stream()
          .filter(e -> e.getValue() == config)
          .map(Map.Entry::getKey)
          .findFirst().orElse(UltraShop.MOD_ID);

        DisplayConfig displayCfg = shop.getDisplayConfig();
        ConditionsConfig conditionsCfg = shop.getConditionsConfig();
        SoundConfig soundCfg = shop.getSoundConfig();


        if (shop.isMaintenance() && !PermissionApi.hasPermission(player, modId + ".admin", 2)
            && !PermissionApi.hasPermission(player, UltraShop.MOD_ID + ".admin", 2)) {
          String shopName = displayCfg != null && displayCfg.getName() != null ? displayCfg.getName() : shop.getId();
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageShopInMaintenance().replace("%shop%", shopName),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }


        if (!PermissionApi.hasPermission(player, shop.getPermission(modId), 4)) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageNotHavePermission()
              .replace("%shop%", titleOf(displayCfg))
              .replace("%permission%", shop.getPermission(modId)),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }


        var openConditions = conditionsCfg != null ? conditionsCfg.getOpenConditions() : null;
        if (openConditions != null && !openConditions.isEmpty()
            && !ConditionUtils.check(openConditions, player)) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageShopNotOpen().replace("%shop%", shop.getId()),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }

        LangConfig lang = ctx.getLang();
        int rows = displayCfg != null ? displayCfg.getRows() : 6;
        ChestTemplate template = ChestTemplate.builder(rows).build();
        if (displayCfg != null && displayCfg.getPanels() != null) {
          PanelsConfig.applyConfig(template, displayCfg.getPanels(), rows);
        }

        Rectangle rectangle =
          displayCfg != null ? displayCfg.getRectangle() : null;
        int totalSlots = rectangle != null ? rectangle.getLength() * rectangle.getWidth() : rows * 9;
        List<Button> buttons = new ArrayList<>();
        boolean hasPagination;

        if (!(shop instanceof CategoryShop categoryShop)) {

          List<Product> products = ShopProducts.activeProducts(shop, modId, player);
          boolean hasRotationSlots = shop instanceof RotationShop rs
            && rs.getRotationSlots() != null && !rs.getRotationSlots().isEmpty();
          boolean autoPlace = !hasRotationSlots && displayCfg != null && displayCfg.isAutoPlace();
          boolean needsPagination = products.size() > totalSlots || autoPlace;
          hasPagination = needsPagination;

          if (needsPagination) {
            for (Product product : products) {
              if (!product.hasErrors()) {
                String bal = PlaceholderReplacer.buildBalanceString(product, shop, player);
                buttons.add(ProductRenderer.createButton(product, player, shop, null, 1, config, nav, withClose, bal));
              }
            }
          } else {
            for (Product product : products) {
              Integer slot = product.getSlot();
              if (slot == null) continue;
              if (UIUtils.isInside(slot, rows)) {
                String bal = PlaceholderReplacer.buildBalanceString(product, shop, player);
                template.set(slot, ProductRenderer.createButton(product, player, shop, null, 1, config, nav, withClose, bal));
              }
            }
          }
        } else {

          boolean autoPlace = displayCfg != null && displayCfg.isAutoPlace();
          boolean needsPagination = autoPlace || categoryShop.getSubShops().size() > totalSlots;
          hasPagination = needsPagination;

          for (SubShop subShop : categoryShop.getSubShops()) {
            GooeyButton btn = createCategoryButton(subShop, player, nav, config, withClose, modId);
            if (btn == null) {
              continue;
            }
            if (needsPagination) {
              buttons.add(btn);
            } else if (UIUtils.isInside(subShop.getSlot(), rows)) {
              template.set(subShop.getSlot(), btn);
            }
          }
        }


        applyInfoButton(template, shop, displayCfg, lang, modId, rows, player);


        applyBalanceButton(template, shop, displayCfg, lang, player, rows);


        ItemModel itemCloseRaw = displayCfg != null ? displayCfg.getItemClose() : null;
        if (itemCloseRaw != null && UIUtils.isInside(itemCloseRaw.getSlot(), rows) && withClose) {
          ItemModel closeItem = LangConfig.resolve(itemCloseRaw, lang.getGlobalItemClose());
          String closeCommand = conditionsCfg != null ? conditionsCfg.getCloseCommand() : null;
          template.set(itemCloseRaw.getSlot(), getButton(closeItem, action -> {
            if (closeCommand != null && !closeCommand.isEmpty()) {
              PlayerUtils.executeCommand(closeCommand, player);
              return;
            }

            Shop parent = nav.goBack();
            if (parent != null) {
              openShop(player, parent, nav, config, withClose);
            } else {
              MainMenuBuilder.open(player, config);
            }
          }));
        }



        if (hasPagination) {

          ItemModel itemPrevRaw = displayCfg != null ? displayCfg.getItemPrevious() : null;
          if (itemPrevRaw != null && UIUtils.isInside(itemPrevRaw.getSlot(), rows)) {
            ItemModel prev = LangConfig.resolve(itemPrevRaw, lang.getGlobalItemPrevious());
            template.set(itemPrevRaw.getSlot(), LinkedPageButton.builder()
              .display(prev.getItemStack()).linkType(LinkType.Previous).build());
          }
          ItemModel itemNextRaw = displayCfg != null ? displayCfg.getItemNext() : null;
          if (itemNextRaw != null && UIUtils.isInside(itemNextRaw.getSlot(), rows)) {
            ItemModel next = LangConfig.resolve(itemNextRaw, lang.getGlobalItemNext());
            template.set(itemNextRaw.getSlot(), LinkedPageButton.builder()
              .display(next.getItemStack()).linkType(LinkType.Next).build());
          }
        }

        String title = titleOf(displayCfg).replace("%shop%", shop.getId());
        String soundOpen = soundCfg != null ? soundCfg.getSoundOpen() : null;
        GooeyPage page;

        if (hasPagination) {
          if (rectangle != null) rectangle.apply(template);
          LinkedPage.Builder linkedPage = LinkedPage.builder()
            .template(template)
            .onOpen(a -> new Sound(soundOpen).playSoundPlayer(a.getPlayer()))
            .title(AdventureTranslator.toNative(title));
          page = PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);
        } else {
          page = GooeyPage.builder()
            .template(template)
            .onOpen(a -> new Sound(soundOpen).playSoundPlayer(player))
            .build();
          page.setTitle(AdventureTranslator.toNative(title));
        }

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error opening shop " + shop.getId(), e);
      }
    });
  }

  /**
   * Navigate to a shop, pushing it onto the navigation stack.
   */
  public static void navigateTo(ServerPlayerEntity player, Shop target, NavigationContext nav,
                                ShopConfig config, boolean withClose) {
    nav.push(target);
    openShop(player, target, nav, config, withClose);
  }



  private static String titleOf(DisplayConfig displayCfg) {
    if (displayCfg == null) return "";
    String t = displayCfg.getTitle();
    return t != null ? t : "";
  }

  private static GooeyButton createCategoryButton(SubShop subShop, ServerPlayerEntity player,
                                                  NavigationContext nav,
                                                  ShopConfig config, boolean withClose, String modId) {
    ShopContext ctx = ShopContext.get();
    List<Shop> shops = ctx.getTypedShops(modId);
    Shop category = shops.stream()
      .filter(s -> s.getId().equals(subShop.getIdShop()))
      .findFirst().orElse(null);

    if (category == null) {
      UltraShop.LOGGER.warn("Sub-shop not found: " + subShop.getIdShop());
      return null;
    }

    DisplayConfig categoryDisplay = category.getDisplayConfig();
    ItemModel raw = categoryDisplay != null ? categoryDisplay.getDisplayItem() : null;
    if (raw == null) return null;
    ItemModel display = LangConfig.resolve(raw, ctx.getLang().getGlobalDisplay());
    List<String> lore = new ArrayList<>(display.getLore());
    return getButton(display,
      display.getDisplayname().replace("%shop%", category.getId()),
      lore,
      action -> navigateTo(player, category, nav, config, withClose));
  }

  private static void applyInfoButton(ChestTemplate template, Shop shop, DisplayConfig displayCfg,
                                      LangConfig lang, String modId, int rows,
                                      ServerPlayerEntity player) {
    ItemModel infoRaw = displayCfg != null ? displayCfg.getItemInfoShop() : null;
    if (infoRaw == null || !UIUtils.isInside(infoRaw.getSlot(), rows)) return;

    ShopContext ctx = ShopContext.get();
    boolean isDynamic = shop instanceof RotationShop;
    ItemModel infoItem = LangConfig.resolve(infoRaw,
      isDynamic ? lang.getShopInfoDynamic() : lang.getShopInfoPermanent());

    List<String> lore = new ArrayList<>(infoItem.getLore());

    if (isDynamic) {
      RotationShop rotShop = (RotationShop) shop;
      long cooldownTimestamp = ctx.getDataShop().getActualCooldown(rotShop, modId, player.getUuid());
      String cooldownStr = cooldownTimestamp > System.currentTimeMillis()
        ? PlayerUtils.getCooldown(cooldownTimestamp)
        : rotShop.isPlayerScoped() ? "Your rotation is refreshing..." : "Rotating...";
      int amount = rotShop.getRotationAmount();
      int totalProducts = rotShop.getProducts() != null ? rotShop.getProducts().size() : 0;

      lore.replaceAll(s -> s
        .replace("%cooldown%", cooldownStr)
        .replace("%number%", String.valueOf(amount))
        .replace("%amountProducts%", String.valueOf(amount))
        .replace("%totalProducts%", String.valueOf(totalProducts))
      );
    }

    String name = infoItem.getDisplayname().replace("%shop%", shop.getId());
    template.set(infoRaw.getSlot(), getButton(infoItem, name, lore, a -> {}));
  }

  private static void applyBalanceButton(ChestTemplate template, Shop shop, DisplayConfig displayCfg,
                                         LangConfig lang, ServerPlayerEntity player, int rows) {
    ItemModel balanceRaw = displayCfg != null ? displayCfg.getItemBalance() : null;
    if (balanceRaw == null || !UIUtils.isInside(balanceRaw.getSlot(), rows)) return;
    ItemModel balanceItem = LangConfig.resolve(balanceRaw, lang.getGlobalItemBalance());
    StringBuilder formatSb = new StringBuilder();
    StringBuilder currencySb = new StringBuilder();
    for (EconomyUse eco : shop.getEconomies()) {
        BigDecimal bal = EconomyApi.getBalance(player.getUuid(), eco);
        formatSb.append(EconomyApi.formatMoney(bal, eco)).append(" ");
        currencySb.append(eco.getCurrency()).append(" ");
    }
    String format = formatSb.toString().trim();
    String currency = currencySb.toString().trim();

    String name = balanceItem.getDisplayname()
      .replace("%balance%", format)
      .replace("%currency%", currency)
      .replace("%amount%", format);
    List<String> lore = new ArrayList<>(balanceItem.getLore());
    lore.replaceAll(s -> s.replace("%balance%", format)
      .replace("%currency%", currency)
      .replace("%amount%", format));
    template.set(balanceRaw.getSlot(), getButton(balanceItem, name, lore, a -> {
    }));
  }

  private static GooeyButton getButton(ItemModel model, Consumer<ButtonAction> onClick) {
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
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .onClick(onClick::accept)
      .build();
  }
}

