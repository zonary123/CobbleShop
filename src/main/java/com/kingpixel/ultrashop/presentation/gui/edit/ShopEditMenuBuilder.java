package com.kingpixel.ultrashop.presentation.gui.edit;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.*;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin GUI for editing shops and products in-game.
 * Supports item, pokemon, and command products via chat input.
 */
public final class ShopEditMenuBuilder {

  private static final String SEP = "§8─────────────────────";

  private ShopEditMenuBuilder() {
  }

  // ======================= SHOP LIST =======================

  public static void openShopList(ServerPlayerEntity player, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Shop> shops = ctx.getShops(modId);
    List<Button> buttons = new ArrayList<>();

    for (Shop shop : shops) {
      List<String> lore = new ArrayList<>();
      lore.add(SEP);

      // Layout
      lore.add("§7Type: §f" + shop.getType());
      lore.add("§7Name: §f" + shop.getName());
      lore.add("§7Rows: §f" + shop.getRows() + "  §7AutoPlace: " + boolIcon(shop.isAutoPlace()));
      lore.add("§7Products: §f" + shop.getProducts().size()
        + "  §7SubShops: §f" + (shop.getSubShops() != null ? shop.getSubShops().size() : 0));

      // Economy
      lore.add("");
      lore.add("§e⛃ Economy");
      for (var eco : shop.getEconomies()) {
        lore.add("  §7• §f" + eco.getEconomyId() + "§8:§f" + eco.getCurrency());
      }
      if (shop.getGlobalDiscount() > 0) {
        lore.add("  §7Global Discount: §a" + shop.getGlobalDiscount() + "%");
      }
      if (!shop.getDiscounts().isEmpty()) {
        lore.add("  §7Permission Discounts: §f" + shop.getDiscounts().size());
      }

      // Rotation
      if (shop.isRotation() && shop.getRotationSchedule() != null) {
        lore.add("");
        lore.add("§d⟳ Rotation");
        if (shop.getRotationSchedule().getCron() != null) {
          lore.add("  §7Cron: §f" + shop.getRotationSchedule().getCron() + " §8(priority)");
        }
        lore.add("  §7Interval: §f" + shop.getRotationSchedule().getInterval());
        lore.add("  §7Amount: §f" + shop.getRotationSchedule().getAmount() + " products");
        lore.add("  §7Announce: " + boolIcon(shop.isAnnounceRotation()));
      }

      // Conditions
      if (!shop.getOpenConditions().isEmpty()) {
        lore.add("");
        lore.add("§c⚡ Conditions §7(" + shop.getOpenConditions().size() + ")");
        for (var cond : shop.getOpenConditions()) {
          lore.add("  §7• §f" + cond.getType());
        }
      }

      lore.add(SEP);
      lore.add("§a▶ Left click §7→ Edit products");
      lore.add("§e▶ Right click §7→ Edit shop settings");

      ItemModel display = LangConfig.resolve(shop.getDisplay(), lang.getGlobalDisplay());

      buttons.add(button(display.getItemStack(), "§6§l" + shop.getId(), lore, action -> {
        switch (action.getClickType()) {
          case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> openShopSettings(player, shop, config, modId);
          default -> openProductList(player, shop, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(49, closeBtn(lang, player));
    template.set(45, prevBtn(lang));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lShop Editor"));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= PRODUCT LIST =======================

  public static void openProductList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    for (int i = 0; i < shop.getProducts().size(); i++) {
      Product product = shop.getProducts().get(i);
      final int idx = i;

      List<String> lore = new ArrayList<>();
      lore.add(SEP);

      // Product type indicator
      String typeTag = productTypeTag(product);
      lore.add("§7Type: " + typeTag);
      lore.add("§7ID: §8" + truncate(product.getProduct(), 40));

      // Pricing
      lore.add("");
      if (product.getPrices() != null && !product.getPrices().isEmpty()) {
        lore.add("§e⛃ Multi-Currency Pricing");
        for (PriceEntry pe : product.getPrices()) {
          String eco = pe.getEconomy() != null ? pe.getEconomy().getCurrency() : "?";
          lore.add("  §7" + eco + ": §aBuy " + fmt(pe.getBuy()) + "  §cSell " + fmt(pe.getSell()));
        }
      } else {
        lore.add("§a⬆ Buy: §f" + fmt(product.getBuy()) + (product.isBuyable() ? "" : " §8(disabled)"));
        lore.add("§c⬇ Sell: §f" + fmt(product.getSell()) + (product.isSellable() ? "" : " §8(disabled)"));
      }

      // Discount
      if (product.getDiscount() != null && product.getDiscount() > 0) {
        lore.add("§e✦ Discount: §f" + product.getDiscount() + "%");
      }

      // Display overrides
      if (product.getDisplayname() != null || product.getDisplay() != null) {
        lore.add("");
        if (product.getDisplayname() != null) lore.add("§7Display Name: §f" + product.getDisplayname());
        if (product.getDisplay() != null) lore.add("§7Display Item: §f" + product.getDisplay());
      }

      // Limits
      if (product.getMax() != null) {
        lore.add("");
        lore.add("§6⏱ Limit: §f" + product.getMax() + " §7every §f" + product.getCooldown() + "min");
      }

      // Rotation
      if (product.getChance() != null) {
        lore.add("§d⟳ Chance: §f" + product.getChance() + "%");
      }

      // Conditions
      if (product.getConditions() != null && !product.getConditions().isEmpty()) {
        lore.add("§c⚡ Conditions: §f" + product.getConditions().size());
      }
      if (product.getVisibilityConditions() != null && !product.getVisibilityConditions().isEmpty()) {
        lore.add("§c👁 Visibility: §f" + product.getVisibilityConditions().size());
      }

      // Flags
      List<String> flags = new ArrayList<>();
      if (Boolean.TRUE.equals(product.getOneByOne())) flags.add("§7OneByOne");
      if (product.getSlot() != null) flags.add("§7Slot:" + product.getSlot());
      if (product.getCustomModelData() != null) flags.add("§7CMD:" + product.getCustomModelData());
      if (product.getLore() != null) flags.add("§7Lore:" + product.getLore().size() + "L");
      if (!flags.isEmpty()) {
        lore.add("§8" + String.join(" §8| ", flags));
      }

      lore.add(SEP);
      lore.add("§a▶ Left click §7→ Edit product");
      lore.add("§c▶ Shift+Right §7→ Delete product");

      ItemStack icon;
      try {
        icon = product.getItemStack();
        if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
      } catch (Exception e) {
        icon = new ItemStack(Items.BARRIER);
      }

      buttons.add(button(icon, typeTag + " §f" + truncate(product.getProduct(), 30), lore, action -> {
        switch (action.getClickType()) {
          case SHIFT_RIGHT_CLICK -> {
            shop.getProducts().remove(idx);
            ConfigLoader.saveShop(shop);
            openProductList(player, shop, config, modId);
          }
          default -> openProductEditor(player, shop, product, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openShopList(player, config, modId)));

    // Add item — opens inventory picker
    template.set(46, button(new ItemStack(Items.CHEST), "§a§l+ Add Item", List.of(
      SEP,
      "§7Pick an item from your inventory or hand",
      "§7to create a new product in this shop.",
      "",
      "§7Default prices: §aBuy 100 §7/ §cSell 50",
      SEP,
      "§a▶ Left click §7→ Pick from inventory",
      "§e▶ Right click §7→ Quick-add item in hand"
    ), a -> {
      switch (a.getClickType()) {
        case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> {
          ItemStack hand = player.getMainHandStack();
          if (!hand.isEmpty()) {
            String itemId = itemStackToProductId(hand);
            Product p = new Product();
            p.setProduct(itemId);
            p.setBuy(BigDecimal.valueOf(100));
            p.setSell(BigDecimal.valueOf(50));
            shop.getProducts().add(p);
            ConfigLoader.saveShop(shop);
            openProductList(player, shop, config, modId);
          }
        }
        default -> openInventoryPicker(player, shop, config, modId);
      }
    }));

    // Add pokemon product
    template.set(47, button(new ItemStack(Items.ENDER_EYE), "§b§l+ Add Pokémon", List.of(
      SEP,
      "§7Add a Pokémon as a product.",
      "§7Type the species in chat after clicking.",
      "",
      "§7Examples:",
      "§f  pikachu",
      "§f  pikachu level:50 shiny:true",
      "",
      "§7Default: §aBuy 1000 §7/ §cSell 0 §7/ OneByOne",
      SEP,
      "§a▶ Click §7→ Enter via chat"
    ), a -> ChatInputManager.requestInput(player, "Enter Pokémon ID (e.g. pikachu, pikachu level:50 shiny:true):", input -> {
      Product p = new Product();
      p.setProduct("pokemon:" + input);
      p.setBuy(BigDecimal.valueOf(1000));
      p.setSell(BigDecimal.ZERO);
      p.setOneByOne(true);
      shop.getProducts().add(p);
      ConfigLoader.saveShop(shop);
      ctx.runOnServer(() -> openProductList(player, shop, config, modId));
    })));

    // Add command product
    template.set(48, button(new ItemStack(Items.COMMAND_BLOCK), "§d§l+ Add Command", List.of(
      SEP,
      "§7Add a command that runs on purchase.",
      "§7Use §f%player% §7for the buyer's name.",
      "",
      "§7Examples:",
      "§f  give %player% diamond 1",
      "§f  effect give %player% speed 60 1",
      "",
      "§7Default: §aBuy 500 §7/ §cSell 0 §7/ OneByOne",
      SEP,
      "§a▶ Click §7→ Enter via chat"
    ), a -> ChatInputManager.requestInput(player, "Enter command (use %player% for buyer):", input -> {
      Product p = new Product();
      p.setProduct("command:" + input);
      p.setBuy(BigDecimal.valueOf(500));
      p.setSell(BigDecimal.ZERO);
      p.setOneByOne(true);
      p.setDisplay("minecraft:paper");
      p.setDisplayname("§dCommand Reward");
      shop.getProducts().add(p);
      ConfigLoader.saveShop(shop);
      ctx.runOnServer(() -> openProductList(player, shop, config, modId));
    })));

    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lProducts: §6" + shop.getId()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ShopContext.get().runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= PRODUCT EDITOR =======================

  public static void openProductEditor(ServerPlayerEntity player, Shop shop, Product product,
                                       ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    ChestTemplate template = ChestTemplate.builder(6).build();

    // Row 0: Product preview (comprehensive)
    ItemStack icon;
    try {
      icon = product.getItemStack();
      if (icon.isEmpty()) icon = new ItemStack(Items.BARRIER);
    } catch (Exception e) {
      icon = new ItemStack(Items.BARRIER);
    }
    List<String> previewLore = new ArrayList<>();
    previewLore.add(SEP);
    previewLore.add("§7Type: " + productTypeTag(product));
    previewLore.add("§7ID: §8" + truncate(product.getProduct(), 40));
    previewLore.add("");

    // Pricing summary
    if (product.getPrices() != null && !product.getPrices().isEmpty()) {
      previewLore.add("§e⛃ Multi-Currency");
      for (PriceEntry pe : product.getPrices()) {
        String eco = pe.getEconomy() != null ? pe.getEconomy().getCurrency() : "?";
        previewLore.add("  §7" + eco + ": §aBuy " + fmt(pe.getBuy()) + " §c Sell " + fmt(pe.getSell()));
      }
    } else {
      previewLore.add("§a⬆ Buy: §f" + fmt(product.getBuy()) + (product.isBuyable() ? " §a✓" : " §c✗"));
      previewLore.add("§c⬇ Sell: §f" + fmt(product.getSell()) + (product.isSellable() ? " §a✓" : " §c✗"));
    }
    if (product.getDiscount() != null) previewLore.add("§e✦ Discount: §f" + product.getDiscount() + "%");

    // Display
    previewLore.add("");
    previewLore.add("§f◆ Display");
    previewLore.add("  §7Name: §f" + (product.getDisplayname() != null ? product.getDisplayname() : "§8auto"));
    previewLore.add("  §7Item: §f" + (product.getDisplay() != null ? product.getDisplay() : "§8none"));
    previewLore.add("  §7CMD: §f" + (product.getCustomModelData() != null ? product.getCustomModelData() : "§8none"));

    // Lore preview
    if (product.getLore() != null && !product.getLore().isEmpty()) {
      previewLore.add("  §7Lore: §f" + product.getLore().size() + " lines");
      for (int li = 0; li < Math.min(product.getLore().size(), 3); li++) {
        previewLore.add("    §8" + truncate(product.getLore().get(li), 35));
      }
      if (product.getLore().size() > 3) previewLore.add("    §8...");
    }

    // Layout & Limits
    previewLore.add("");
    previewLore.add("§f◆ Settings");
    previewLore.add("  §7Slot: §f" + (product.getSlot() != null ? product.getSlot() : "§8auto"));
    previewLore.add("  §7OneByOne: " + boolIcon(Boolean.TRUE.equals(product.getOneByOne())));
    previewLore.add("  §7Stack: §f" + safeMaxStack(product));
    if (product.getMax() != null) {
      previewLore.add("  §7Limit: §f" + product.getMax() + " §7every §f" + product.getCooldown() + "min");
      previewLore.add("  §7UUID: §8" + (product.getUuid() != null ? product.getUuid().toString().substring(0, 8) + "..." : "none"));
    }
    if (product.getChance() != null) {
      previewLore.add("  §7Rotation Chance: §f" + product.getChance() + "%");
    }

    // Conditions
    if (product.getConditions() != null && !product.getConditions().isEmpty()) {
      previewLore.add("");
      previewLore.add("§c⚡ Buy Conditions §7(" + product.getConditions().size() + ")");
      for (var cond : product.getConditions()) {
        previewLore.add("  §7• §f" + cond.getType());
      }
    }
    if (product.getVisibilityConditions() != null && !product.getVisibilityConditions().isEmpty()) {
      previewLore.add("§c👁 Visibility §7(" + product.getVisibilityConditions().size() + ")");
      for (var cond : product.getVisibilityConditions()) {
        previewLore.add("  §7• §f" + cond.getType());
      }
    }

    // Errors
    if (product.hasErrors()) {
      previewLore.add("");
      previewLore.add("§c§l⚠ ERROR: Sell price > Buy price!");
    }

    previewLore.add(SEP);
    template.set(4, button(icon, "§e§l" + truncate(product.getProduct(), 25), previewLore, a -> {
    }));

    // Row 1: Buy price controls
    template.set(9, label(Items.EMERALD, "§a§l--- Buy Price ---",
      List.of("§7Current: §a" + fmt(product.getBuy()),
        product.isBuyable() ? "§a✓ Buyers can purchase this" : "§c✗ Not buyable (price is 0)")));
    template.set(10, priceBtn("§a+100", product::getBuy, v -> product.setBuy(v), 100, shop, player, product, config, modId));
    template.set(11, priceBtn("§a+10", product::getBuy, v -> product.setBuy(v), 10, shop, player, product, config, modId));
    template.set(12, priceBtn("§a+1", product::getBuy, v -> product.setBuy(v), 1, shop, player, product, config, modId));
    template.set(13, priceBtn("§c-1", product::getBuy, v -> product.setBuy(v), -1, shop, player, product, config, modId));
    template.set(14, priceBtn("§c-10", product::getBuy, v -> product.setBuy(v), -10, shop, player, product, config, modId));
    template.set(15, priceBtn("§c-100", product::getBuy, v -> product.setBuy(v), -100, shop, player, product, config, modId));
    template.set(16, button(new ItemStack(Items.OAK_SIGN), "§a✎ Set Exact Buy Price",
      List.of("§7Current: §a" + fmt(product.getBuy()), "", "§7Type a number in chat."),
      a -> ChatInputManager.requestInput(player, "Enter exact buy price:", input -> {
        try {
          product.setBuy(new BigDecimal(input));
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
        } catch (NumberFormatException e) {
          com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player, "&cInvalid number: " + input, "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
        }
      })));

    // Row 2: Sell price controls
    template.set(18, label(Items.REDSTONE, "§c§l--- Sell Price ---",
      List.of("§7Current: §c" + fmt(product.getSell()),
        product.isSellable() ? "§a✓ Players can sell this" : "§c✗ Not sellable (price is 0)",
        product.canBeSold() ? "" : "§8(commands/pokemon/multi-items can't be sold)")));
    template.set(19, priceBtn("§a+100", product::getSell, v -> product.setSell(v), 100, shop, player, product, config, modId));
    template.set(20, priceBtn("§a+10", product::getSell, v -> product.setSell(v), 10, shop, player, product, config, modId));
    template.set(21, priceBtn("§a+1", product::getSell, v -> product.setSell(v), 1, shop, player, product, config, modId));
    template.set(22, priceBtn("§c-1", product::getSell, v -> product.setSell(v), -1, shop, player, product, config, modId));
    template.set(23, priceBtn("§c-10", product::getSell, v -> product.setSell(v), -10, shop, player, product, config, modId));
    template.set(24, priceBtn("§c-100", product::getSell, v -> product.setSell(v), -100, shop, player, product, config, modId));
    template.set(25, button(new ItemStack(Items.OAK_SIGN), "§c✎ Set Exact Sell Price",
      List.of("§7Current: §c" + fmt(product.getSell()), "", "§7Type a number in chat."),
      a -> ChatInputManager.requestInput(player, "Enter exact sell price:", input -> {
        try {
          product.setSell(new BigDecimal(input));
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
        } catch (NumberFormatException e) {
          com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player, "&cInvalid number: " + input, "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
        }
      })));

    // Row 3: Display & properties
    template.set(27, button(new ItemStack(Items.NAME_TAG), "§e✎ Display Name",
      List.of(SEP,
        "§7Current: §f" + (product.getDisplayname() != null ? product.getDisplayname() : "§8auto (from item)"),
        "",
        "§7Overrides the item name shown in the shop.",
        "§7Supports §6& §7color codes and §6<#hex> §7format.",
        SEP,
        "§a▶ Click §7→ Set via chat",
        "§c▶ Shift §7→ Clear (use item name)"),
      a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          product.setDisplayname(null);
          ConfigLoader.saveShop(shop);
          openProductEditor(player, shop, product, config, modId);
        } else ChatInputManager.requestInput(player, "Enter display name (supports & color codes):", input -> {
          product.setDisplayname(input);
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
        });
      }));

    template.set(28, button(new ItemStack(Items.PAINTING), "§e✎ Display Item",
      List.of(SEP,
        "§7Current: §f" + (product.getDisplay() != null ? product.getDisplay() : "§8none (uses product item)"),
        "",
        "§7Overrides the icon shown in the shop GUI.",
        "§7Useful for commands/pokemon to show a",
        "§7representative item instead of barrier.",
        SEP,
        "§a▶ Left §7→ Set from hand",
        "§e▶ Right §7→ Set via chat",
        "§c▶ Shift §7→ Clear"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setDisplay(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case RIGHT_CLICK -> ChatInputManager.requestInput(player, "Enter display item ID:", input -> {
            product.setDisplay(input);
            ConfigLoader.saveShop(shop);
            ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
          });
          default -> {
            ItemStack hand = player.getMainHandStack();
            if (!hand.isEmpty()) {
              product.setDisplay(net.minecraft.registry.Registries.ITEM.getId(hand.getItem()).toString());
              ConfigLoader.saveShop(shop);
              openProductEditor(player, shop, product, config, modId);
            }
          }
        }
      }));

    template.set(29, button(new ItemStack(Items.GOLD_NUGGET), "§e✦ Discount",
      List.of(SEP,
        "§7Current: §e" + (product.getDiscount() != null ? product.getDiscount() + "%" : "§8none"),
        "",
        "§7Per-product discount applied to buy price.",
        "§7Stacks with shop's global discount.",
        SEP,
        "§a▶ Left §7→ +5%",
        "§c▶ Right §7→ -5%",
        "§e▶ Shift §7→ Clear"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setDiscount(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            float c = product.getDiscount() != null ? product.getDiscount() : 0f;
            product.setDiscount(Math.min(c + 5f, 100f));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            float c = product.getDiscount() != null ? product.getDiscount() : 0f;
            float n = Math.max(c - 5f, 0f);
            product.setDiscount(n > 0f ? n : null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    template.set(30, button(new ItemStack(Boolean.TRUE.equals(product.getOneByOne()) ? Items.IRON_BARS : Items.GRAY_DYE),
      "§eOneByOne: " + boolIcon(Boolean.TRUE.equals(product.getOneByOne())),
      List.of(SEP,
        "§7Current: " + boolIcon(Boolean.TRUE.equals(product.getOneByOne())),
        "",
        "§7When enabled, forces stack size to 1.",
        "§7Players buy/sell one at a time.",
        "§7Auto-enabled for pokemon & commands.",
        SEP,
        "§a▶ Click §7→ Toggle"),
      a -> {
        product.setOneByOne(Boolean.TRUE.equals(product.getOneByOne()) ? null : true);
        ConfigLoader.saveShop(shop);
        openProductEditor(player, shop, product, config, modId);
      }));

    template.set(31, button(new ItemStack(Items.RABBIT_FOOT), "§d⟳ Rotation Chance",
      List.of(SEP,
        "§7Current: §d" + (product.getChance() != null ? product.getChance() + "%" : "§f100% §8(default)"),
        "",
        "§7Weight for dynamic rotation selection.",
        "§7Higher = more likely to appear.",
        "§7Only relevant if the shop has rotation.",
        SEP,
        "§a▶ Left §7→ +10",
        "§c▶ Right §7→ -10",
        "§e▶ Shift §7→ Clear (100%)"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setChance(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            int c = product.getChance() != null ? product.getChance() : 100;
            product.setChance(c + 10);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            int c = product.getChance() != null ? product.getChance() : 100;
            product.setChance(Math.max(c - 10, 1));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    // Row 4: Limits & lore
    template.set(36, button(new ItemStack(Items.IRON_DOOR), "§6⏱ Max Purchases",
      List.of(SEP,
        "§7Max: §f" + (product.getMax() != null ? product.getMax() : "§8unlimited"),
        "§7Cooldown: §f" + (product.getCooldown() != null ? product.getCooldown() + " minutes" : "§8none"),
        "§7UUID: §8" + (product.getUuid() != null ? product.getUuid().toString().substring(0, 8) + "..." : "auto-generated"),
        "",
        "§7Limits how many times a player can buy.",
        "§7Resets after the cooldown period.",
        SEP,
        "§a▶ Left §7→ +1",
        "§c▶ Right §7→ -1",
        "§a▶ Shift+Left §7→ +10",
        "§c▶ Shift+Right §7→ Clear (unlimited)"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_RIGHT_CLICK -> {
            product.setMax(null);
            product.setCooldown(null);
            product.setUuid(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case SHIFT_LEFT_CLICK -> {
            product.setMax((product.getMax() != null ? product.getMax() : 0) + 10);
            if (product.getCooldown() == null) product.setCooldown(60);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            product.setMax((product.getMax() != null ? product.getMax() : 0) + 1);
            if (product.getCooldown() == null) product.setCooldown(60);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            if (product.getMax() != null && product.getMax() > 1) product.setMax(product.getMax() - 1);
            else {
              product.setMax(null);
              product.setCooldown(null);
              product.setUuid(null);
            }
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    template.set(37, button(new ItemStack(Items.CLOCK), "§6⏱ Cooldown (minutes)",
      List.of(SEP,
        "§7Current: §f" + (product.getCooldown() != null ? product.getCooldown() + " minutes" : "§8none"),
        "",
        "§7Time before the purchase limit resets.",
        "§7Requires §fMax Purchases §7to be set.",
        SEP,
        "§a▶ Left §7→ +10min",
        "§c▶ Right §7→ -10min",
        "§e▶ Shift §7→ Set exact via chat"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK ->
            ChatInputManager.requestInput(player, "Enter cooldown in minutes:", input -> {
              try {
                int m = Integer.parseInt(input);
                product.setCooldown(m > 0 ? m : null);
                ConfigLoader.saveShop(shop);
                ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
              } catch (NumberFormatException e) {
                com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player, "&cInvalid number", "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
              }
            });
          case LEFT_CLICK -> {
            int c = product.getCooldown() != null ? product.getCooldown() : 0;
            product.setCooldown(c + 10);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            if (product.getCooldown() != null && product.getCooldown() > 10)
              product.setCooldown(product.getCooldown() - 10);
            else product.setCooldown(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    template.set(38, button(new ItemStack(Items.WRITABLE_BOOK), "§e✎ Change Product ID",
      List.of(SEP,
        "§7Current: §f" + truncate(product.getProduct(), 35),
        "§7Type: " + productTypeTag(product),
        "§7Max Stack: §f" + safeMaxStack(product),
        "",
        "§7Supported formats:",
        "§f  minecraft:diamond",
        "§f  item:1:minecraft:diamond#[...]",
        "§f  pokemon:pikachu level:50",
        "§f  command:give %player% diamond 1",
        SEP,
        "§a▶ Left §7→ Pick from inventory",
        "§e▶ Right §7→ Type in chat"),
      a -> {
        switch (a.getClickType()) {
          case RIGHT_CLICK, SHIFT_RIGHT_CLICK ->
            ChatInputManager.requestInput(player, "Enter product ID (item/pokemon:/command:):", input -> {
              product.setProduct(input);
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
            });
          default -> openInventoryPickerForEdit(player, shop, product, config, modId);
        }
      }));

    template.set(39, button(new ItemStack(Items.ITEM_FRAME), "§e⊞ Slot Position",
      List.of(SEP,
        "§7Current: §f" + (product.getSlot() != null ? "Slot " + product.getSlot() : "§8auto (shop fills grid)"),
        "",
        "§7Fixed slot in the shop GUI.",
        "§7Only used when AutoPlace is OFF.",
        "§7Slots: 0-" + ((shop.getRows() * 9) - 1),
        SEP,
        "§a▶ Left §7→ +1",
        "§c▶ Right §7→ -1",
        "§e▶ Shift §7→ Clear (auto)"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setSlot(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            int c = product.getSlot() != null ? product.getSlot() : 0;
            product.setSlot(Math.min(c + 1, 53));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            int c = product.getSlot() != null ? product.getSlot() : 0;
            product.setSlot(Math.max(c - 1, 0));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    // Lore editor with preview
    List<String> loreBtnLore = new ArrayList<>();
    loreBtnLore.add(SEP);
    loreBtnLore.add("§7Lines: §f" + (product.getLore() != null ? product.getLore().size() : 0));
    if (product.getLore() != null && !product.getLore().isEmpty()) {
      loreBtnLore.add("");
      loreBtnLore.add("§7Preview:");
      for (int li = 0; li < Math.min(product.getLore().size(), 5); li++) {
        loreBtnLore.add("  §8" + (li + 1) + ". §7" + truncate(product.getLore().get(li), 32));
      }
      if (product.getLore().size() > 5) loreBtnLore.add("  §8... +" + (product.getLore().size() - 5) + " more");
    }
    loreBtnLore.add(SEP);
    loreBtnLore.add("§a▶ Left §7→ Add line via chat");
    loreBtnLore.add("§c▶ Right §7→ Remove last line");
    loreBtnLore.add("§c▶ Shift §7→ Clear all");

    template.set(40, button(new ItemStack(Items.BOOK), "§e✎ Lore", loreBtnLore,
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setLore(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case RIGHT_CLICK -> {
            if (product.getLore() != null && !product.getLore().isEmpty()) {
              List<String> l = new ArrayList<>(product.getLore());
              l.remove(l.size() - 1);
              product.setLore(l.isEmpty() ? null : l);
              ConfigLoader.saveShop(shop);
            }
            openProductEditor(player, shop, product, config, modId);
          }
          default -> ChatInputManager.requestInput(player, "Enter lore line (supports & colors):", input -> {
            List<String> l = product.getLore() != null ? new ArrayList<>(product.getLore()) : new ArrayList<>();
            l.add(input);
            product.setLore(l);
            ConfigLoader.saveShop(shop);
            ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
          });
        }
      }));

    // Row 4 continued: Conditions
    {
      int condCount = product.getConditions() != null ? product.getConditions().size() : 0;
      List<String> condLore = new ArrayList<>();
      condLore.add(SEP);
      if (condCount == 0) {
        condLore.add("§7No buy conditions — anyone can buy.");
      } else {
        for (int ci = 0; ci < product.getConditions().size(); ci++) {
          condLore.add("§7" + (ci + 1) + ". §f" + product.getConditions().get(ci).getType());
        }
      }
      condLore.add(SEP);
      condLore.add("§7Conditions checked BEFORE a player");
      condLore.add("§7can purchase this product.");
      condLore.add(SEP);
      condLore.add("§a▶ Click §7→ Manage conditions");
      template.set(41, button(new ItemStack(Items.IRON_BARS),
        "§c⚡ Buy Conditions §7(" + condCount + ")",
        condLore, a -> openProductConditionsList(player, shop, product, config, modId, false)));
    }

    {
      int visCount = product.getVisibilityConditions() != null ? product.getVisibilityConditions().size() : 0;
      List<String> visLore = new ArrayList<>();
      visLore.add(SEP);
      if (visCount == 0) {
        visLore.add("§7No visibility conditions — always visible.");
      } else {
        for (int ci = 0; ci < product.getVisibilityConditions().size(); ci++) {
          visLore.add("§7" + (ci + 1) + ". §f" + product.getVisibilityConditions().get(ci).getType());
        }
      }
      visLore.add(SEP);
      visLore.add("§7Conditions that determine if this");
      visLore.add("§7product is shown in the shop GUI.");
      visLore.add(SEP);
      visLore.add("§a▶ Click §7→ Manage conditions");
      template.set(42, button(new ItemStack(Items.ENDER_EYE),
        "§c👁 Visibility Conditions §7(" + visCount + ")",
        visLore, a -> openProductConditionsList(player, shop, product, config, modId, true)));
    }

    // Bottom nav
    template.set(45, backBtn(lang, a -> openProductList(player, shop, config, modId)));
    template.set(49, closeBtn(lang, player));

    GooeyPage page = GooeyPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lEdit: §6" + truncate(product.getProduct(), 20)))
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= SHOP SETTINGS =======================

  public static void openShopSettings(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    ChestTemplate template = ChestTemplate.builder(4).build();

    // Row 0: Basic info
    template.set(0, button(new ItemStack(Items.NAME_TAG), "§e✎ Name",
      List.of(SEP, "§7Current: §f" + shop.getName(), "",
        "§7Internal display name of this shop.", SEP, "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter shop name:", input -> {
        shop.setName(input);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(1, button(new ItemStack(Items.OAK_SIGN), "§e✎ Title",
      List.of(SEP, "§7Current: §f" + shop.getTitle(), "",
        "§7GUI window title. Use §f%shop% §7for shop id.", SEP, "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter GUI title (use %shop%):", input -> {
        shop.setTitle(input);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(2, button(new ItemStack(shop.isAutoPlace() ? Items.LIME_DYE : Items.GRAY_DYE),
      "§eAutoPlace: " + boolIcon(shop.isAutoPlace()),
      List.of(SEP, "§7Current: " + boolIcon(shop.isAutoPlace()), "",
        "§7When ON, products fill the grid automatically.",
        "§7When OFF, each product needs a slot number.", SEP,
        "§a▶ Click §7→ Toggle"),
      a -> {
        shop.setAutoPlace(!shop.isAutoPlace());
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    template.set(3, button(new ItemStack(Items.OAK_STAIRS, Math.max(1, shop.getRows())),
      "§eRows: §f" + shop.getRows(),
      List.of(SEP, "§7Current: §f" + shop.getRows() + " rows §8(" + (shop.getRows() * 9) + " slots)", "",
        "§7Number of rows in the shop chest GUI.", SEP,
        "§a▶ Left §7→ +1", "§c▶ Right §7→ -1"),
      a -> {
        if (a.getClickType().name().contains("LEFT")) shop.setRows(Math.min(shop.getRows() + 1, 6));
        else shop.setRows(Math.max(shop.getRows() - 1, 1));
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    template.set(4, button(new ItemStack(Items.GOLD_INGOT),
      "§e✦ Global Discount: §f" + shop.getGlobalDiscount() + "%",
      List.of(SEP, "§7Current: §e" + shop.getGlobalDiscount() + "%", "",
        "§7Applied to ALL buy prices in this shop.",
        "§7Stacks with per-product discounts.", SEP,
        "§a▶ Left §7→ +5%", "§c▶ Right §7→ -5%"),
      a -> {
        if (a.getClickType().name().contains("LEFT"))
          shop.setGlobalDiscount(Math.min(shop.getGlobalDiscount() + 5f, 100f));
        else shop.setGlobalDiscount(Math.max(shop.getGlobalDiscount() - 5f, 0f));
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    // Permission discounts (read-only)
    {
      List<String> discLore = new ArrayList<>();
      discLore.add(SEP);
      if (shop.getDiscounts().isEmpty()) {
        discLore.add("§7No permission discounts configured.");
      } else {
        for (var entry : shop.getDiscounts().entrySet()) {
          discLore.add("§7" + entry.getKey() + " §8→ §e" + entry.getValue() + "%");
        }
      }
      discLore.add(SEP);
      discLore.add("§8Edit in JSON: §7discounts");
      template.set(5, button(new ItemStack(Items.EXPERIENCE_BOTTLE),
        "§e✦ Permission Discounts §7(" + shop.getDiscounts().size() + ")",
        discLore, a -> {
        }));
    }

    // Economies (read-only)
    {
      List<String> ecoLore = new ArrayList<>();
      ecoLore.add(SEP);
      for (var eco : shop.getEconomies()) {
        ecoLore.add("§7• §f" + eco.getEconomyId() + " §8: §f" + eco.getCurrency());
      }
      ecoLore.add(SEP);
      ecoLore.add("§8Edit in JSON: §7economies");
      template.set(6, button(new ItemStack(Items.DIAMOND),
        "§e⛃ Economies §7(" + shop.getEconomies().size() + ")",
        ecoLore, a -> {
        }));
    }

    // Row 1: Sounds & behavior
    template.set(9, button(new ItemStack(Items.NOTE_BLOCK), "§e♪ Sound Open",
      List.of(SEP, "§7Current: §f" + orEmpty(shop.getSoundOpen()), "",
        "§7Sound played when the shop GUI opens.",
        "§7Example: §fminecraft:block.chest.open", SEP,
        "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter open sound (e.g. minecraft:block.chest.open):", input -> {
        shop.setSoundOpen(input);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(10, button(new ItemStack(Items.NOTE_BLOCK), "§e♪ Sound Close",
      List.of(SEP, "§7Current: §f" + orEmpty(shop.getSoundClose()), "",
        "§7Sound played when the shop GUI closes.", SEP,
        "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter close sound:", input -> {
        shop.setSoundClose(input);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(11, button(new ItemStack(Items.LEVER), "§e⚙ Close Command",
      List.of(SEP, "§7Current: §f" + orEmpty(shop.getCloseCommand()), "",
        "§7Command that runs when close button is clicked.",
        "§7Use §f%player% §7for the player's name.", SEP,
        "§a▶ Click §7→ Set", "§c▶ Shift §7→ Clear"),
      a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          shop.setCloseCommand("");
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        } else ChatInputManager.requestInput(player, "Enter close command:", input -> {
          shop.setCloseCommand(input);
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
        });
      }));

    template.set(12, button(new ItemStack(shop.isAnnounceRotation() ? Items.BELL : Items.GRAY_DYE),
      "§e📢 Announce Rotation: " + boolIcon(shop.isAnnounceRotation()),
      List.of(SEP, "§7Current: " + boolIcon(shop.isAnnounceRotation()), "",
        "§7Broadcasts a message to all players when",
        "§7the shop's products rotate.", SEP,
        "§a▶ Click §7→ Toggle"),
      a -> {
        shop.setAnnounceRotation(!shop.isAnnounceRotation());
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    template.set(13, button(new ItemStack(Items.SPYGLASS), "§e🎨 Color Prefix",
      List.of(SEP, "§7Current: §f" + orEmpty(shop.getColorProduct()), "",
        "§7Color prefix added to product names.",
        "§7Example: §6&6 §7→ gold text", SEP,
        "§a▶ Click §7→ Set", "§c▶ Shift §7→ Clear"),
      a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          shop.setColorProduct("");
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        } else ChatInputManager.requestInput(player, "Enter color prefix (e.g. &6, <#ff0000>):", input -> {
          shop.setColorProduct(input);
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
        });
      }));

    // Open Conditions
    {
      List<String> condLore = new ArrayList<>();
      condLore.add(SEP);
      if (shop.getOpenConditions().isEmpty()) {
        condLore.add("§7No conditions — shop is always open.");
      } else {
        for (int i = 0; i < shop.getOpenConditions().size(); i++) {
          var cond = shop.getOpenConditions().get(i);
          condLore.add("§7" + (i + 1) + ". §f" + cond.getType());
        }
      }
      condLore.add(SEP);
      condLore.add("§a▶ Click §7→ Manage conditions");
      template.set(14, button(new ItemStack(Items.IRON_BARS),
        "§c⚡ Open Conditions §7(" + shop.getOpenConditions().size() + ")",
        condLore, a -> openConditionsList(player, shop, config, modId)));
    }

    // Row 2: Rotation schedule
    {
      List<String> rotLore = new ArrayList<>();
      rotLore.add(SEP);
      rotLore.add("§7Type: §f" + shop.getType());
      if (shop.getRotationSchedule() != null) {
        String cronStr = shop.getRotationSchedule().getCron();
        rotLore.add("§7Cron: §f" + (cronStr != null && !cronStr.isBlank() ? cronStr + " §8(priority)" : "§8none"));
        rotLore.add("§7Interval: §f" + shop.getRotationSchedule().getInterval()
          + (cronStr != null && !cronStr.isBlank() ? " §8(ignored — cron set)" : ""));
        rotLore.add("§7Amount: §f" + shop.getRotationSchedule().getAmount() + " products per rotation");
        if (shop.isRotation()) {
          long next = ShopContext.get().getDataShop().getActualCooldown(shop, modId);
          if (next > 0) {
            rotLore.add("§7Next rotation: §f" + java.time.Instant.ofEpochMilli(next));
          }
        }
      } else {
        rotLore.add("§7No rotation — type is " + shop.getType() + ".");
      }
      rotLore.add(SEP);
      rotLore.add("§a▶ Left §7→ Set interval (e.g. 30m, 1h, 7d)");
      rotLore.add("§e▶ Right §7→ Set amount");
      rotLore.add("§d▶ Middle §7→ Set cron expression (overrides interval)");
      rotLore.add("§c▶ Shift §7→ Remove rotation (back to NORMAL)");

      template.set(18, button(new ItemStack(Items.REPEATER), "§d⟳ Rotation Schedule", rotLore,
        a -> {
          switch (a.getClickType()) {
            case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
              shop.setRotationSchedule(null);
              shop.setType(ShopType.NORMAL);
              ConfigLoader.saveShop(shop);
              openShopSettings(player, shop, config, modId);
            }
            case RIGHT_CLICK -> ChatInputManager.requestInput(player, "Enter rotation amount:", input -> {
              try {
                int amt = Integer.parseInt(input);
                if (shop.getRotationSchedule() == null) shop.setRotationSchedule(new RotationSchedule("1h", amt));
                else shop.getRotationSchedule().setAmount(amt);
                shop.setType(ShopType.ROTATION);
                ConfigLoader.saveShop(shop);
                ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
              } catch (NumberFormatException e) {
                PlayerUtils.sendMessage(player, "&cInvalid number", "", TypeMessage.CHAT);
              }
            });
            case MIDDLE_CLICK -> ChatInputManager.requestInput(player, "Enter cron (e.g. 0 18 * * 5):", input -> {
              try {
                CronExpression.parse(input); // validate
              } catch (Exception e) {
                PlayerUtils.sendMessage(player, "&cInvalid cron: " + e.getMessage(), "", TypeMessage.CHAT);
                return;
              }
              if (shop.getRotationSchedule() == null) shop.setRotationSchedule(new RotationSchedule("1h", 3));
              shop.getRotationSchedule().setCron(input);
              shop.setType(ShopType.ROTATION);
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
            });
            default -> ChatInputManager.requestInput(player, "Enter interval (e.g. 30m, 1h, 12h, 7d):", input -> {
              if (shop.getRotationSchedule() == null) shop.setRotationSchedule(new RotationSchedule(input, 3));
              else shop.getRotationSchedule().setInterval(input);
              shop.setType(ShopType.ROTATION);
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
            });
          }
        }));
    }

    // Bottom nav
    template.set(27, backBtn(lang, a -> openShopList(player, config, modId)));
    template.set(31, closeBtn(lang, player));

    GooeyPage page = GooeyPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lSettings: §6" + shop.getId()))
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= INVENTORY PICKER =======================

  /**
   * Opens a GUI showing all unique items in the player's inventory.
   * Clicking an item adds it as a new product to the shop.
   */
  public static void openInventoryPicker(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    // Collect unique items from inventory (main + offhand + armor)
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    List<ItemStack> allStacks = new ArrayList<>();
    allStacks.addAll(player.getInventory().main);
    allStacks.addAll(player.getInventory().armor);
    allStacks.add(player.getInventory().offHand.getFirst());

    for (ItemStack stack : allStacks) {
      if (stack.isEmpty()) continue;
      String productId = itemStackToProductId(stack);
      String simpleId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
      if (!seen.add(productId)) continue;

      ItemStack display = stack.copy();
      display.setCount(1);

      boolean hasComponents = !productId.equals(simpleId);
      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Item: §f" + simpleId);
      if (hasComponents) {
        lore.add("§7Components: §a✓ §8(enchantments, data, etc.)");
        lore.add("§7Product ID: §8" + truncate(productId, 38));
      }
      lore.add("§7In inventory: §f" + countItem(player, stack));
      lore.add("§7Max stack: §f" + stack.getMaxCount());
      lore.add(SEP);
      lore.add("§a▶ Click §7→ Add product §8(buy=100, sell=50)");
      lore.add("§e▶ Shift §7→ Add sell-only §8(buy=0, sell=50)");

      final String finalProductId = productId;
      buttons.add(GooeyButton.builder()
        .display(display)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("§f" + simpleId + (hasComponents ? " §a[+]" : "")))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
        .onClick(a -> {
          Product p = new Product();
          p.setProduct(finalProductId);
          if (a.getClickType().name().contains("SHIFT")) {
            p.setBuy(BigDecimal.ZERO);
            p.setSell(BigDecimal.valueOf(50));
          } else {
            p.setBuy(BigDecimal.valueOf(100));
            p.setSell(BigDecimal.valueOf(50));
          }
          shop.getProducts().add(p);
          ConfigLoader.saveShop(shop);
          openProductList(player, shop, config, modId);
        })
        .build());
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openProductList(player, shop, config, modId)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lPick Item from Inventory"));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  private static int countItem(ServerPlayerEntity player, ItemStack target) {
    int count = 0;
    for (ItemStack stack : player.getInventory().main) {
      if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, target)) {
        count += stack.getCount();
      }
    }
    return count;
  }

  /**
   * Opens inventory picker to change an existing product's item ID.
   */
  public static void openInventoryPickerForEdit(ServerPlayerEntity player, Shop shop, Product product,
                                                ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    List<ItemStack> allStacks = new ArrayList<>(player.getInventory().main);
    allStacks.addAll(player.getInventory().armor);
    allStacks.add(player.getInventory().offHand.getFirst());

    for (ItemStack stack : allStacks) {
      if (stack.isEmpty()) continue;
      String productId = itemStackToProductId(stack);
      String simpleId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
      if (!seen.add(productId)) continue;

      ItemStack display = stack.copy();
      display.setCount(1);

      boolean hasComponents = !productId.equals(simpleId);
      final String finalProductId = productId;
      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Item: §f" + simpleId);
      if (hasComponents) lore.add("§7Components: §a✓");
      lore.add("§7Max stack: §f" + stack.getMaxCount());
      lore.add(SEP);
      lore.add("§a▶ Click §7→ Set as product ID");

      buttons.add(GooeyButton.builder()
        .display(display)
        .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("§f" + simpleId + (hasComponents ? " §a[+]" : "")))
        .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
        .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
        .onClick(a -> {
          product.setProduct(finalProductId);
          ConfigLoader.saveShop(shop);
          openProductEditor(player, shop, product, config, modId);
        })
        .build());
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openProductEditor(player, shop, product, config, modId)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lPick Item for: §6" + truncate(product.getProduct(), 18)));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= CONDITIONS EDITOR =======================

  /**
   * Opens a menu listing the shop's current open conditions.
   * Players can add new conditions (with defaults) or remove existing ones.
   * Fine-tuning values should be done in the JSON file.
   */
  public static void openConditionsList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    for (int i = 0; i < shop.getOpenConditions().size(); i++) {
      var cond = shop.getOpenConditions().get(i);
      final int idx = i;

      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Type: §f" + cond.getType());
      // Show toString split into lines for readability
      String condStr = cond.toString();
      if (condStr.length() > 45) {
        lore.add("§8" + truncate(condStr, 45));
        if (condStr.length() > 45) lore.add("§8" + truncate(condStr.substring(45), 45));
      } else {
        lore.add("§8" + condStr);
      }
      lore.add(SEP);
      lore.add("§8Fine-tune values in the JSON file.");
      lore.add("§c▶ Shift+Right click §7→ Remove");

      buttons.add(button(new ItemStack(Items.PAPER), "§e#" + (i + 1) + " §f" + cond.getType(), lore, a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          shop.getOpenConditions().remove(idx);
          ConfigLoader.saveShop(shop);
          openConditionsList(player, shop, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openShopSettings(player, shop, config, modId)));

    // Add condition button
    template.set(46, button(new ItemStack(Items.LIME_DYE), "§a§l+ Add Condition",
      List.of(SEP,
        "§7Choose a condition type to add.",
        "§7It will be created with default values.",
        "§7Edit the specific values in the JSON.",
        SEP,
        "§a▶ Click §7→ Choose type"),
      a -> openConditionTypeSelector(player, shop, config, modId)));

    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lConditions: §6" + shop.getId()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  /**
   * Opens a menu showing all registered condition types from CobbleUtils.
   * Clicking one creates a default instance and adds it to the shop.
   */
  @SuppressWarnings("unchecked")
  public static void openConditionTypeSelector(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    // Access the TYPES map from ConditionAdapter via reflection
    Map<String, Class<? extends com.kingpixel.cobbleutils.Model.conditions.Condition>> types = getConditionTypes();
    if (types == null || types.isEmpty()) {
      com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player,
        "&cCould not load condition types from CobbleUtils.", "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
      openConditionsList(player, shop, config, modId);
      return;
    }

    for (var entry : types.entrySet()) {
      String typeName = entry.getKey();
      Class<? extends com.kingpixel.cobbleutils.Model.conditions.Condition> clazz = entry.getValue();

      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Class: §8" + clazz.getSimpleName());
      lore.add("");
      lore.add("§7Creates a new §f" + typeName + " §7condition");
      lore.add("§7with default values. Edit the JSON to");
      lore.add("§7customize the specific parameters.");
      lore.add(SEP);
      lore.add("§a▶ Click §7→ Add to shop");

      buttons.add(button(new ItemStack(Items.CHAIN), "§e" + typeName, lore, a -> {
        try {
          var condition = clazz.getDeclaredConstructor().newInstance();
          shop.getOpenConditions().add(condition);
          ConfigLoader.saveShop(shop);
          openConditionsList(player, shop, config, modId);
        } catch (Exception e) {
          com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player,
            "&cFailed to create condition: " + e.getMessage(), "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openConditionsList(player, shop, config, modId)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lAdd Condition"));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  /**
   * Retrieves the registered condition types from ConditionAdapter via reflection.
   * Returns null if the field is inaccessible.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Class<? extends Condition>> getConditionTypes() {
    try {
      var field = com.kingpixel.cobbleutils.adapter.ConditionAdapter.class.getDeclaredField("TYPES");
      field.setAccessible(true);
      return (Map<String, Class<? extends com.kingpixel.cobbleutils.Model.conditions.Condition>>) field.get(null);
    } catch (Exception e) {
      return null;
    }
  }

  // ======================= PRODUCT CONDITIONS EDITOR =======================

  /**
   * Opens a menu listing a product's conditions (buy or visibility).
   *
   * @param isVisibility true for visibilityConditions, false for buy conditions
   */
  public static void openProductConditionsList(ServerPlayerEntity player, Shop shop, Product product,
                                               ShopConfig config, String modId, boolean isVisibility) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    List<Condition> conditions = isVisibility ? product.getVisibilityConditions() : product.getConditions();
    if (conditions == null) conditions = new ArrayList<>();
    final List<Condition> condList = conditions;

    for (int i = 0; i < condList.size(); i++) {
      var cond = condList.get(i);
      final int idx = i;

      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Type: §f" + cond.getType());
      String condStr = cond.toString();
      if (condStr.length() > 45) {
        lore.add("§8" + truncate(condStr, 45));
        if (condStr.length() > 45) lore.add("§8" + truncate(condStr.substring(45), 45));
      } else {
        lore.add("§8" + condStr);
      }
      lore.add(SEP);
      lore.add("§8Fine-tune values in the JSON file.");
      lore.add("§c▶ Shift+Right click §7→ Remove");

      buttons.add(button(new ItemStack(Items.PAPER), "§e#" + (i + 1) + " §f" + cond.getType(), lore, a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          condList.remove(idx);
          if (isVisibility) {
            product.setVisibilityConditions(condList.isEmpty() ? null : condList);
          } else {
            product.setConditions(condList.isEmpty() ? null : condList);
          }
          ConfigLoader.saveShop(shop);
          openProductConditionsList(player, shop, product, config, modId, isVisibility);
        }
      }));
    }

    String label = isVisibility ? "Visibility" : "Buy";
    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openProductEditor(player, shop, product, config, modId)));

    template.set(46, button(new ItemStack(Items.LIME_DYE), "§a§l+ Add Condition",
      List.of(SEP,
        "§7Choose a condition type to add.",
        "§7It will be created with default values.",
        "§7Edit the specific values in the JSON.",
        SEP,
        "§a▶ Click §7→ Choose type"),
      a -> openProductConditionTypeSelector(player, shop, product, config, modId, isVisibility)));

    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§l" + label + " Conditions: §6" + truncate(product.getProduct(), 15)));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  /**
   * Opens a menu showing all registered condition types for product conditions.
   */
  @SuppressWarnings("unchecked")
  public static void openProductConditionTypeSelector(ServerPlayerEntity player, Shop shop, Product product,
                                                      ShopConfig config, String modId, boolean isVisibility) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();

    Map<String, Class<? extends Condition>> types = getConditionTypes();
    if (types == null || types.isEmpty()) {
      com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player,
        "&cCould not load condition types from CobbleUtils.", "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
      openProductConditionsList(player, shop, product, config, modId, isVisibility);
      return;
    }

    for (var entry : types.entrySet()) {
      String typeName = entry.getKey();
      Class<? extends Condition> clazz = entry.getValue();

      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Class: §8" + clazz.getSimpleName());
      lore.add("");
      lore.add("§7Creates a new §f" + typeName + " §7condition");
      lore.add("§7with default values. Edit the JSON to");
      lore.add("§7customize the specific parameters.");
      lore.add(SEP);
      lore.add("§a▶ Click §7→ Add to product");

      buttons.add(button(new ItemStack(Items.CHAIN), "§e" + typeName, lore, a -> {
        try {
          var condition = clazz.getDeclaredConstructor().newInstance();
          if (isVisibility) {
            List<Condition> vis = product.getVisibilityConditions() != null
              ? new ArrayList<>(product.getVisibilityConditions()) : new ArrayList<>();
            vis.add(condition);
            product.setVisibilityConditions(vis);
          } else {
            List<Condition> conds = product.getConditions() != null
              ? new ArrayList<>(product.getConditions()) : new ArrayList<>();
            conds.add(condition);
            product.setConditions(conds);
          }
          ConfigLoader.saveShop(shop);
          openProductConditionsList(player, shop, product, config, modId, isVisibility);
        } catch (Exception e) {
          com.kingpixel.cobbleutils.util.PlayerUtils.sendMessage(player,
            "&cFailed to create condition: " + e.getMessage(), "", com.kingpixel.cobbleutils.util.TypeMessage.CHAT);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openProductConditionsList(player, shop, product, config, modId, isVisibility)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative("§6§lAdd Condition"));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  // ======================= HELPERS =======================

  private static GooeyButton button(ItemStack icon, String name, List<String> lore,
                                    java.util.function.Consumer<ca.landonjw.gooeylibs2.api.button.ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(icon)
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
      .onClick(onClick::accept)
      .build();
  }

  private static GooeyButton label(net.minecraft.item.Item item, String name) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .build();
  }

  private static GooeyButton label(net.minecraft.item.Item item, String name, List<String> lore) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .build();
  }

  private static GooeyButton priceBtn(String label, java.util.function.Supplier<BigDecimal> getter,
                                      java.util.function.Consumer<BigDecimal> setter, int delta,
                                      Shop shop, ServerPlayerEntity player, Product product,
                                      ShopConfig config, String modId) {
    net.minecraft.item.Item item = delta > 0 ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
    BigDecimal current = getter.get() != null ? getter.get() : BigDecimal.ZERO;
    return GooeyButton.builder()
      .display(new ItemStack(item, Math.min(Math.abs(delta), 64)))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(label))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(List.of(
        "§7Current: §f" + current.setScale(2, RoundingMode.HALF_UP).toPlainString(),
        "§7Change: §f" + (delta > 0 ? "+" : "") + delta,
        "§7Result: §f" + current.add(BigDecimal.valueOf(delta)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString()
      ))))
      .onClick(a -> {
        BigDecimal cur = getter.get() != null ? getter.get() : BigDecimal.ZERO;
        BigDecimal next = cur.add(BigDecimal.valueOf(delta)).max(BigDecimal.ZERO);
        setter.accept(next);
        ConfigLoader.saveShop(shop);
        openProductEditor(player, shop, product, config, modId);
      })
      .build();
  }

  private static GooeyButton closeBtn(LangConfig lang, ServerPlayerEntity player) {
    return GooeyButton.builder()
      .display(lang.getGlobalItemClose().getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("§c✕ Close"))
      .onClick(a -> UIManager.closeUI(player))
      .build();
  }

  private static GooeyButton backBtn(LangConfig lang, java.util.function.Consumer<ca.landonjw.gooeylibs2.api.button.ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(lang.getGlobalItemPrevious().getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative("§7← Back"))
      .onClick(onClick::accept)
      .build();
  }

  private static LinkedPageButton prevBtn(LangConfig lang) {
    return LinkedPageButton.builder()
      .display(lang.getGlobalItemPrevious().getItemStack()).linkType(LinkType.Previous).build();
  }

  private static LinkedPageButton nextBtn(LangConfig lang) {
    return LinkedPageButton.builder()
      .display(lang.getGlobalItemNext().getItemStack()).linkType(LinkType.Next).build();
  }

  private static String fmt(BigDecimal value) {
    return value != null ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
  }

  private static String orEmpty(String value) {
    return value != null && !value.isEmpty() ? value : "§8none";
  }

  private static String boolIcon(boolean value) {
    return value ? "§a✓ Yes" : "§c✗ No";
  }

  private static String truncate(String s, int max) {
    if (s == null) return "§8null";
    return s.length() > max ? s.substring(0, max) + "§8..." : s;
  }

  private static String productTypeTag(Product product) {
    String id = product.getProduct();
    if (id == null) return "§8[?]";
    if (id.startsWith("command:")) return "§d[CMD]";
    if (id.startsWith("pokemon:")) return "§b[PKM]";
    if (id.startsWith("item:") || id.contains("#")) return "§a[ITEM+]";
    return "§f[ITEM]";
  }

  private static int safeMaxStack(Product product) {
    try {
      return product.getMaxStack();
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * Converts an ItemStack to the product ID format used by CobbleUtils ItemChance.
   * <p>Format: {@code item:1:minecraft:netherite_sword#[key=value,...]}
   * <p>The {@code #} is CobbleUtils' separator between item ID and component data.
   * When the part after {@code #} does NOT start with {@code {}, CobbleUtils concatenates
   * {@code itemId + nbtString} and parses it with Brigadier's ItemStringReader, which
   * understands the 1.20.5+ component syntax {@code item[components]}.
   * <p>If the item has no custom components, returns just {@code minecraft:item_id}.
   */
  private static String itemStackToProductId(ItemStack stack) {
    String itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();

    try {
      // RegistryOps is REQUIRED — enchantments and other registry-backed components
      // fail silently with plain NbtOps, producing empty data instead of actual values.
      var registryOps = com.kingpixel.cobbleutils.CobbleUtils.server
        .getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);

      var nbtElement = net.minecraft.item.ItemStack.CODEC
        .encodeStart(registryOps, stack)
        .getOrThrow();

      if (nbtElement instanceof net.minecraft.nbt.NbtCompound compound && compound.contains("components")) {
        var components = compound.getCompound("components");
        if (components != null && !components.isEmpty()) {
          // Build component string: [key=value,key=value]
          // CobbleUtils will concat itemId + this to form: minecraft:item[key=value]
          // and parse it with ItemStringReader (Brigadier)
          StringBuilder sb = new StringBuilder();
          sb.append("[");
          boolean first = true;
          for (String key : components.getKeys()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(key).append("=").append(components.get(key).toString());
          }
          sb.append("]");
          // item:1:itemId#[components] — # splits ID from component data
          return "item:1:" + itemId + "#" + sb;
        }
      }
    } catch (Exception e) {
      // Fallback: just use basic ID
    }

    return itemId;
  }
}
