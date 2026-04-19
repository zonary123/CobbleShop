package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
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
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
          .map(java.util.Map.Entry::getKey)
          .findFirst().orElse(UltraShop.MOD_ID);

        // Check permission
        if (!PermissionApi.hasPermission(player, shop.getPermission(modId), 4)) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageNotHavePermission()
              .replace("%shop%", shop.getTitle())
              .replace("%permission%", shop.getPermission(modId)),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }

        // Check open conditions
        if (!shop.getOpenConditions().isEmpty() && !ConditionUtils.check(shop.getOpenConditions(), player)) {
          PlayerUtils.sendMessage(player,
            ctx.getLang().getMessageShopNotOpen().replace("%shop%", shop.getId()),
            ctx.getLang().getPrefix(), TypeMessage.CHAT);
          return;
        }

        LangConfig lang = ctx.getLang();
        ChestTemplate template = ChestTemplate.builder(shop.getRows()).build();
        PanelsConfig.applyConfig(template, shop.getPanels(), shop.getRows());

        int totalSlots = shop.getRectangle().getLength() * shop.getRectangle().getWidth();
        List<Button> buttons = new ArrayList<>();

        if (!shop.hasCategories()) {
          // Products mode
          List<Product> products = getActiveProducts(shop, modId);
          boolean needsPagination = products.size() > totalSlots || shop.isAutoPlace();

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
              if (UIUtils.isInside(slot, shop.getRows())) {
                String bal = PlaceholderReplacer.buildBalanceString(product, shop, player);
                template.set(slot, ProductRenderer.createButton(product, player, shop, null, 1, config, nav, withClose, bal));
              }
            }
          }
        } else {
          // Categories mode
          for (SubShop subShop : shop.getSubShops()) {
            GooeyButton btn = createCategoryButton(subShop, player, shop, nav, config, withClose, modId);
            if (btn != null) {
              if (shop.isAutoPlace()) {
                buttons.add(btn);
              } else if (UIUtils.isInside(subShop.getSlot(), shop.getRows())) {
                template.set(subShop.getSlot(), btn);
              }
            }
          }
        }

        // Shop info button
        applyInfoButton(template, shop, lang, modId);

        // Balance button
        applyBalanceButton(template, shop, lang, player);

        // Close button
        if (UIUtils.isInside(shop.getItemClose().getSlot(), shop.getRows()) && withClose) {
          ItemModel closeItem = LangConfig.resolve(shop.getItemClose(), lang.getGlobalItemClose());
          template.set(shop.getItemClose().getSlot(), closeItem.getButton(1, action -> {
            if (shop.getCloseCommand() != null && !shop.getCloseCommand().isEmpty()) {
              PlayerUtils.executeCommand(shop.getCloseCommand(), player);
              return;
            }
            // Go back
            Shop parent = nav.goBack();
            if (parent != null) {
              openShop(player, parent, nav, config, withClose);
            } else {
              MainMenuBuilder.open(player, config);
            }
          }));
        }

        // Build page (with or without pagination)
        boolean hasPagination = !buttons.isEmpty();

        if (hasPagination) {
          // Pagination navigation
          if (UIUtils.isInside(shop.getItemPrevious().getSlot(), shop.getRows())) {
            ItemModel prev = LangConfig.resolve(shop.getItemPrevious(), lang.getGlobalItemPrevious());
            template.set(shop.getItemPrevious().getSlot(), LinkedPageButton.builder()
              .display(prev.getItemStack()).linkType(LinkType.Previous).build());
          }
          if (UIUtils.isInside(shop.getItemNext().getSlot(), shop.getRows())) {
            ItemModel next = LangConfig.resolve(shop.getItemNext(), lang.getGlobalItemNext());
            template.set(shop.getItemNext().getSlot(), LinkedPageButton.builder()
              .display(next.getItemStack()).linkType(LinkType.Next).build());
          }
        }

        String title = shop.getTitle().replace("%shop%", shop.getId());
        GooeyPage page;

        if (hasPagination) {
          shop.getRectangle().apply(template);
          LinkedPage.Builder linkedPage = LinkedPage.builder()
            .template(template)
            .onOpen(a -> new Sound(shop.getSoundOpen()).playSoundPlayer(a.getPlayer()))
            .title(AdventureTranslator.toNative(title));
          page = PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);
        } else {
          page = GooeyPage.builder()
            .template(template)
            .onOpen(a -> new Sound(shop.getSoundOpen()).playSoundPlayer(player))
            .build();
          page.setTitle(AdventureTranslator.toNative(title));
        }

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error opening shop " + shop.getId() + ": " + e.getMessage());
        e.printStackTrace();
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

  // --- Private helpers ---

  private static List<Product> getActiveProducts(Shop shop, String modId) {
    if (shop.getRotationSchedule() != null) {
      return ShopContext.get().getDataShop().updateDynamicProducts(shop, modId, false);
    }
    return shop.getProducts();
  }

  private static GooeyButton createCategoryButton(SubShop subShop, ServerPlayerEntity player,
                                                  Shop parentShop, NavigationContext nav,
                                                  ShopConfig config, boolean withClose, String modId) {
    ShopContext ctx = ShopContext.get();
    List<Shop> shops = ctx.getShops(modId);
    Shop category = shops.stream()
      .filter(s -> s.getId().equals(subShop.getIdShop()))
      .findFirst().orElse(null);

    if (category == null) {
      UltraShop.LOGGER.warn("Sub-shop not found: " + subShop.getIdShop());
      return null;
    }

    ItemModel display = LangConfig.resolve(category.getDisplay(), ctx.getLang().getGlobalDisplay());
    List<String> lore = new ArrayList<>(display.getLore());
    return display.getButton(1,
      display.getDisplayname().replace("%shop%", category.getId()),
      lore,
      action -> navigateTo(player, category, nav, config, withClose));
  }

  private static void applyInfoButton(ChestTemplate template, Shop shop, LangConfig lang, String modId) {
    if (!UIUtils.isInside(shop.getItemInfoShop().getSlot(), shop.getRows())) return;

    ShopContext ctx = ShopContext.get();
    boolean isDynamic = shop.getRotationSchedule() != null;
    ItemModel infoItem = LangConfig.resolve(shop.getItemInfoShop(),
      isDynamic ? lang.getShopInfoDynamic() : lang.getShopInfoPermanent());

    List<String> lore = new ArrayList<>(infoItem.getLore());

    if (isDynamic) {
      long cooldownTimestamp = ctx.getDataShop().getActualCooldown(shop, modId);
      String cooldownStr = cooldownTimestamp > System.currentTimeMillis()
        ? PlayerUtils.getCooldown(cooldownTimestamp)
        : "Rotating...";
      int amount = shop.getRotationSchedule().getAmount();

      lore.replaceAll(s -> s
        .replace("%cooldown%", cooldownStr)
        .replace("%number%", String.valueOf(amount))
        .replace("%amountProducts%", String.valueOf(amount))
        .replace("%totalProducts%", String.valueOf(shop.getProducts().size()))
      );
    }

    String name = infoItem.getDisplayname().replace("%shop%", shop.getId());
    template.set(shop.getItemInfoShop().getSlot(), infoItem.getButton(1, name, lore, a -> {}));
  }

  private static void applyBalanceButton(ChestTemplate template, Shop shop, LangConfig lang, ServerPlayerEntity player) {
    if (!UIUtils.isInside(shop.getItemBalance().getSlot(), shop.getRows())) return;
    ItemModel balanceItem = LangConfig.resolve(shop.getItemBalance(), lang.getGlobalItemBalance());
    StringBuilder formatSb = new StringBuilder();
    StringBuilder currencySb = new StringBuilder();
    for (com.kingpixel.cobbleutils.Model.EconomyUse eco : shop.getEconomies()) {
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
    template.set(shop.getItemBalance().getSlot(), balanceItem.getButton(1, name, lore, a -> {
    }));
  }
}

