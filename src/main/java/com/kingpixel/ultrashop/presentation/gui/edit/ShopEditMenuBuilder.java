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
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.model.UserInfo;
import com.kingpixel.ultrashop.domain.model.PriceEntry;
import com.kingpixel.ultrashop.domain.model.StockMode;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.AbstractShop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.CronExpression;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.item.Item;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import com.kingpixel.cobbleutils.adapter.ConditionAdapter;
import com.kingpixel.cobbleutils.Model.EconomyUse;

/**
 * Admin GUI for editing shops and products in-game.
 * Supports item, pokemon, and command products via chat input.
 */
public final class ShopEditMenuBuilder {

  private static final String SEP = "§8─────────────────────";

  private ShopEditMenuBuilder() {
  }



  public static void openShopList(ServerPlayerEntity player, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Shop> shops = ctx.getTypedShops(modId);
    List<Button> buttons = new ArrayList<>();

    for (Shop shop : shops) {
      List<String> lore = new ArrayList<>();
      lore.add(SEP);


      lore.add("§7Type: §f" + shop.getType());
      lore.add("§7Name: §f" + (shop.getDisplayConfig() != null ? shop.getDisplayConfig().getName() : ""));
      lore.add("§7Rows: §f" + (shop.getDisplayConfig() != null ? shop.getDisplayConfig().getRows() : 6) + "  §7AutoPlace: " + boolIcon(shop.isAutoPlace()));

      int productCount = 0;
      int subShopCount = 0;
      if (shop instanceof NormalShop normal) {
        productCount = normal.getProducts() != null ? normal.getProducts().size() : 0;
      } else if (shop instanceof RotationShop rotation) {
        productCount = rotation.getProductPool() != null ? rotation.getProductPool().size() : 0;
      } else if (shop instanceof CategoryShop category) {
        subShopCount = category.getSubShops() != null ? category.getSubShops().size() : 0;
      }

      lore.add("§7Products: §f" + productCount
        + "  §7SubShops: §f" + subShopCount);


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


      if (shop instanceof RotationShop r && r.getScheduler() != null) {
        lore.add("");
        lore.add("§d⟳ Rotation");
        if (r.getScheduler() instanceof CronScheduler cron) {
          lore.add("  §7Cron: §f" + cron.getExpression() + " §8(priority)");
        } else if (r.getScheduler() instanceof DurationScheduler dur) {
          lore.add("  §7Interval: §f" + dur.getDuration());
        }
        lore.add("  §7Amount: §f" + r.getRotationAmount() + " products");
        lore.add("  §7Announce: " + boolIcon(shop.getConditionsConfig() != null && shop.getConditionsConfig().isAnnounceRotation()));
      }


      var conditions = shop.getConditionsConfig() != null && shop.getConditionsConfig().getOpenConditions() != null
        ? shop.getConditionsConfig().getOpenConditions() : List.<Condition>of();
      if (!conditions.isEmpty()) {
        lore.add("");
        lore.add("§c⚡ Conditions §7(" + conditions.size() + ")");
        for (var cond : conditions) {
          lore.add("  §7• §f" + cond.getType());
        }
      }

      lore.add(SEP);
      lore.add("§a▶ Left click §7→ Edit products");
      lore.add("§e▶ Right click §7→ Edit shop settings");

      ItemModel display = LangConfig.resolve(shop.getDisplayConfig() != null ? shop.getDisplayConfig().getDisplayItem() : null, lang.getGlobalDisplay());

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


    template.set(47, button(new ItemStack(Items.WRITABLE_BOOK), "§a§l+ Create Shop", List.of(
      SEP,
      "§7Create a new shop directly from here.",
      "§7You will be prompted to enter the ID",
      "§7and type (normal, rotation, category) in chat.",
      SEP,
      "§a▶ Click §7→ Create new shop"
    ), a -> ChatInputManager.requestInput(player, "Enter new shop ID (alphanumeric, no spaces):", inputId -> {
      String cleanId = inputId.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
      if (cleanId.isEmpty()) {
        PlayerUtils.sendMessage(player, "§cInvalid ID.", lang.getPrefix(), TypeMessage.CHAT);
        return;
      }
      boolean exists = ShopContext.get().getTypedShops(modId).stream()
        .anyMatch(s -> s.getId().equalsIgnoreCase(cleanId));
      if (exists) {
        PlayerUtils.sendMessage(player, lang.getCommandShopAlreadyExists().replace("%shop%", cleanId), lang.getPrefix(), TypeMessage.CHAT);
        return;
      }
      ctx.runOnServer(() -> ChatInputManager.requestInput(player, "Enter shop type (normal / rotation / category):", inputType -> {
        String typeStr = inputType.trim().toUpperCase();
        Shop newShop;
        if (typeStr.equals("ROTATION")) {
          RotationShop r = new RotationShop();
          r.setId(cleanId);
          r.setScheduler(new DurationScheduler("30m"));
          r.setRotationAmount(3);
          newShop = r;
        } else if (typeStr.equals("CATEGORY")) {
          CategoryShop c = new CategoryShop();
          c.setId(cleanId);
          c.setSubShops(new ArrayList<>());
          newShop = c;
        } else {
          NormalShop n = new NormalShop();
          n.setId(cleanId);
          newShop = n;
        }

        ShopOptionsApi op = ShopOptionsApi.builder().modId(modId).path(modId + "/").build();
        ConfigLoader.createShop(op, newShop);
        PlayerUtils.sendMessage(player, lang.getCommandShopCreated().replace("%shop%", cleanId), lang.getPrefix(), TypeMessage.CHAT);
        ctx.runOnServer(() -> openShopList(player, config, modId));
      }));
    })));

    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleShopList()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  private static List<Product> getEditableProducts(Shop shop) {
    if (shop instanceof NormalShop normal) {
      return normal.getProducts();
    } else if (shop instanceof RotationShop rotation) {
      return rotation.getProductPool();
    }
    return new ArrayList<>();
  }



  public static void openProductList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();
    List<Product> products = getEditableProducts(shop);

    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      final int idx = i;

      List<String> lore = new ArrayList<>();
      lore.add(SEP);


      String typeTag = productTypeTag(product);
      lore.add("§7Type: " + typeTag);
      lore.add("§7ID: §8" + truncate(product.getProduct(), 40));


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


      if (product.getDiscount() != null && product.getDiscount() > 0) {
        lore.add("§e✦ Discount: §f" + product.getDiscount() + "%");
      }


      if (product.getDisplayname() != null || product.getDisplay() != null) {
        lore.add("");
        if (product.getDisplayname() != null) lore.add("§7Display Name: §f" + product.getDisplayname());
        if (product.getDisplay() != null) lore.add("§7Display Item: §f" + product.getDisplay());
      }


      if (product.getMax() != null) {
        lore.add("");
        lore.add("§6⏱ Limit: §f" + product.getMax() + " §7every §f" + product.getCooldown());
      }


      if (product.getChance() != null) {
        lore.add("§d⟳ Chance: §f" + product.getChance() + "%");
      }


      if (product.getConditions() != null && !product.getConditions().isEmpty()) {
        lore.add("§c⚡ Conditions: §f" + product.getConditions().size());
      }
      if (product.getVisibilityConditions() != null && !product.getVisibilityConditions().isEmpty()) {
        lore.add("§c👁 Visibility: §f" + product.getVisibilityConditions().size());
      }


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
            products.remove(idx);
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
            openProductList(player, shop, config, modId);
          }
          default -> openProductEditor(player, shop, product, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openShopList(player, config, modId)));


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
            products.add(p);
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
            openProductList(player, shop, config, modId);
          }
        }
        default -> openInventoryPicker(player, shop, config, modId);
      }
    }));


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
      products.add(p);
      ctx.replaceShop(modId, shop);
      ConfigLoader.saveShop(shop);
      ctx.runOnServer(() -> openProductList(player, shop, config, modId));
    })));


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
      products.add(p);
      ctx.replaceShop(modId, shop);
      ConfigLoader.saveShop(shop);
      ctx.runOnServer(() -> openProductList(player, shop, config, modId));
    })));

    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleProductList().replace("%shop%", shop.getId())));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ShopContext.get().runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  public static void openProductEditor(ServerPlayerEntity player, Shop shop, Product product,
                                       ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    ChestTemplate template = ChestTemplate.builder(6).build();


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


    previewLore.add("");
    previewLore.add("§f◆ Display");
    previewLore.add("  §7Name: §f" + (product.getDisplayname() != null ? product.getDisplayname() : "§8auto"));
    previewLore.add("  §7Item: §f" + (product.getDisplay() != null ? product.getDisplay() : "§8none"));
    previewLore.add("  §7CMD: §f" + (product.getCustomModelData() != null ? product.getCustomModelData() : "§8none"));


    if (product.getLore() != null && !product.getLore().isEmpty()) {
      previewLore.add("  §7Lore: §f" + product.getLore().size() + " lines");
      for (int li = 0; li < Math.min(product.getLore().size(), 3); li++) {
        previewLore.add("    §8" + truncate(product.getLore().get(li), 35));
      }
      if (product.getLore().size() > 3) previewLore.add("    §8...");
    }


    previewLore.add("");
    previewLore.add("§f◆ Settings");
    previewLore.add("  §7Slot: §f" + (product.getSlot() != null ? product.getSlot() : "§8auto"));
    previewLore.add("  §7OneByOne: " + boolIcon(Boolean.TRUE.equals(product.getOneByOne())));
    previewLore.add("  §7Stack: §f" + safeMaxStack(product));
    if (product.getMax() != null) {
      previewLore.add("  §7Limit: §f" + product.getMax() + " §7every §f" + product.getCooldown());
      previewLore.add("  §7UUID: §8" + (product.getUuid() != null ? product.getUuid().toString().substring(0, 8) + "..." : "none"));
    }
    if (product.getSellMax() != null) {
      previewLore.add("  §7Sell Limit: §f" + product.getSellMax() + " §7every §f" + product.getSellCooldown());
      previewLore.add("  §7Sell UUID: §8" + (product.getSellUuid() != null ? product.getSellUuid().toString().substring(0, 8) + "..." : "none"));
    }
    if (product.hasStockControl()) {
      previewLore.add("  §7Stock: §f" + product.getStockAmount() + " §8(" + product.getStockMode() + ")");
    }
    if (product.getChance() != null) {
      previewLore.add("  §7Rotation Chance: §f" + product.getChance() + "%");
    }


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


    if (product.hasErrors()) {
      previewLore.add("");
      previewLore.add("§c§l⚠ ERROR: Sell price > Buy price!");
    }

    previewLore.add(SEP);
    template.set(4, button(icon, "§e§l" + truncate(product.getProduct(), 25), previewLore, a -> {
    }));


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
      a -> ChatInputManager.requestInput(player, lang.getEditorPromptExactBuyPrice(), input -> {
        try {
          product.setBuy(new BigDecimal(input));
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
        } catch (NumberFormatException e) {
          sendConfiguredMessage(player, lang.getMessageInvalidNumber().replace("%input%", input));
        }
      })));


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
      a -> ChatInputManager.requestInput(player, lang.getEditorPromptExactSellPrice(), input -> {
        try {
          product.setSell(new BigDecimal(input));
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
        } catch (NumberFormatException e) {
          sendConfiguredMessage(player, lang.getMessageInvalidNumber().replace("%input%", input));
        }
      })));


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


    template.set(36, button(new ItemStack(Items.IRON_DOOR), "§6⏱ Max Purchases",
      List.of(SEP,
        "§7Max: §f" + (product.getMax() != null ? product.getMax() : "§8unlimited"),
        "§7Cooldown: §f" + (product.getCooldown() != null ? product.getCooldown() : "§8none"),
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
            if (product.getCooldown() == null) product.setCooldown("60m");
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            product.setMax((product.getMax() != null ? product.getMax() : 0) + 1);
            if (product.getCooldown() == null) product.setCooldown("60m");
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

    template.set(37, button(new ItemStack(Items.CLOCK), "§6⏱ Cooldown (duration/cron)",
      List.of(SEP,
        "§7Current: §f" + (product.getCooldown() != null ? product.getCooldown() : "§8none"),
        "",
        "§7Time before the purchase limit resets.",
        "§7Requires §fMax Purchases §7to be set.",
        SEP,
        "§a▶ Left §7→ +10m",
        "§c▶ Right §7→ -10m",
        "§e▶ Shift §7→ Set exact via chat"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK ->
            ChatInputManager.requestInput(player, "Enter cooldown (e.g. 60m, 1d, 0 0 * * *):", input -> {
              if (input == null || input.isBlank() || input.equalsIgnoreCase("none")) {
                product.setCooldown(null);
              } else {
                product.setCooldown(input.trim());
              }
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
            });
          case LEFT_CLICK -> {
            product.setCooldown(addMinutesToCooldown(product.getCooldown(), 10));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            product.setCooldown(addMinutesToCooldown(product.getCooldown(), -10));
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
        "§7Slots: 0-" + (((shop.getDisplayConfig() != null ? shop.getDisplayConfig().getRows() : 6) * 9) - 1),
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

    template.set(43, button(new ItemStack(Items.CHEST), lang.getEditorButtonStockControl(),
      List.of(SEP,
        "§7Enabled: " + boolIcon(product.hasStockControl()),
        "§7Amount: §f" + (product.getStockAmount() != null ? product.getStockAmount() : "§8none"),
        "§7Mode: §f" + (product.getStockMode() != null ? product.getStockMode() : "§8none"),
        "",
        "§7PLAYER: stock per player",
        "§7GLOBAL: shared stock for all players",
        SEP,
        "§a▶ Left §7→ +1 stock",
        "§c▶ Right §7→ -1 stock",
        "§e▶ Middle §7→ Toggle mode",
        "§c▶ Shift §7→ Disable stock"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK -> {
            product.setStockAmount(null);
            product.setStockMode(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case MIDDLE_CLICK -> {
            if (product.getStockAmount() == null || product.getStockAmount() <= 0) {
              product.setStockAmount(1);
            }
            StockMode mode = product.getStockMode();
            product.setStockMode(mode == StockMode.GLOBAL ? StockMode.PLAYER : StockMode.GLOBAL);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            int current = product.getStockAmount() != null ? product.getStockAmount() : 0;
            product.setStockAmount(current + 1);
            if (product.getStockMode() == null) product.setStockMode(StockMode.PLAYER);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            if (product.getStockAmount() != null && product.getStockAmount() > 1) {
              product.setStockAmount(product.getStockAmount() - 1);
            } else {
              product.setStockAmount(null);
              product.setStockMode(null);
            }
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));


    template.set(45, backBtn(lang, a -> openProductList(player, shop, config, modId)));

    template.set(46, button(new ItemStack(Items.HOPPER), "§6⏱ Max Sales (Limit)",
      List.of(SEP,
        "§7Max Sales: §f" + (product.getSellMax() != null ? product.getSellMax() : "§8unlimited"),
        "§7Cooldown: §f" + (product.getSellCooldown() != null ? product.getSellCooldown() : "§8none"),
        "§7UUID: §8" + (product.getSellUuid() != null ? product.getSellUuid().toString().substring(0, 8) + "..." : "auto-generated"),
        "",
        "§7Limits how many times a player can sell.",
        "§7Resets after the cooldown period.",
        SEP,
        "§a▶ Left §7→ +1",
        "§c▶ Right §7→ -1",
        "§a▶ Shift+Left §7→ +10",
        "§c▶ Shift+Right §7→ Clear (unlimited)"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_RIGHT_CLICK -> {
            product.setSellMax(null);
            product.setSellCooldown(null);
            product.setSellUuid(null);
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case SHIFT_LEFT_CLICK -> {
            product.setSellMax((product.getSellMax() != null ? product.getSellMax() : 0) + 10);
            if (product.getSellCooldown() == null) product.setSellCooldown("60m");
            if (product.getSellUuid() == null) product.setSellUuid(UUID.randomUUID());
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          case LEFT_CLICK -> {
            product.setSellMax((product.getSellMax() != null ? product.getSellMax() : 0) + 1);
            if (product.getSellCooldown() == null) product.setSellCooldown("60m");
            if (product.getSellUuid() == null) product.setSellUuid(UUID.randomUUID());
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            if (product.getSellMax() != null && product.getSellMax() > 1) {
              product.setSellMax(product.getSellMax() - 1);
            } else {
              product.setSellMax(null);
              product.setSellCooldown(null);
              product.setSellUuid(null);
            }
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    template.set(47, button(new ItemStack(Items.CLOCK), "§6⏱ Sell Cooldown (duration/cron)",
      List.of(SEP,
        "§7Current: §f" + (product.getSellCooldown() != null ? product.getSellCooldown() : "§8none"),
        "",
        "§7Time before the sell limit resets.",
        "§7Requires §fMax Sales §7to be set.",
        SEP,
        "§a▶ Left §7→ +10m",
        "§c▶ Right §7→ -10m",
        "§e▶ Shift §7→ Set exact via chat"),
      a -> {
        switch (a.getClickType()) {
          case SHIFT_LEFT_CLICK, SHIFT_RIGHT_CLICK ->
            ChatInputManager.requestInput(player, "Enter sell cooldown (e.g. 60m, 1d, 0 0 * * *):", input -> {
              if (input == null || input.isBlank() || input.equalsIgnoreCase("none")) {
                product.setSellCooldown(null);
              } else {
                product.setSellCooldown(input.trim());
              }
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openProductEditor(player, shop, product, config, modId));
            });
          case LEFT_CLICK -> {
            product.setSellCooldown(addMinutesToCooldown(product.getSellCooldown(), 10));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
          default -> {
            product.setSellCooldown(addMinutesToCooldown(product.getSellCooldown(), -10));
            ConfigLoader.saveShop(shop);
            openProductEditor(player, shop, product, config, modId);
          }
        }
      }));

    template.set(49, closeBtn(lang, player));

    GooeyPage page = GooeyPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleProductEdit().replace("%product%", truncate(product.getProduct(), 20))))
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  public static void openShopSettings(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    ChestTemplate template = ChestTemplate.builder(4).build();
    AbstractShop absShop = (AbstractShop) shop;


    template.set(0, button(new ItemStack(Items.NAME_TAG), "§e✎ Name",
      List.of(SEP, "§7Current: §f" + (shop.getDisplayConfig() != null ? shop.getDisplayConfig().getName() : ""), "",
        "§7Internal display name of this shop.", SEP, "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter shop name:", input -> {
        shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().name(input).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(1, button(new ItemStack(Items.OAK_SIGN), "§e✎ Title",
      List.of(SEP, "§7Current: §f" + (shop.getDisplayConfig() != null ? shop.getDisplayConfig().getTitle() : ""), "",
        "§7GUI window title. Use §f%shop% §7for shop id.", SEP, "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter GUI title (use %shop%):", input -> {
        shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().title(input).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    template.set(2, button(new ItemStack((shop.getDisplayConfig() != null && shop.getDisplayConfig().isAutoPlace()) ? Items.LIME_DYE : Items.GRAY_DYE),
      "§eAutoPlace: " + boolIcon(shop.getDisplayConfig() != null && shop.getDisplayConfig().isAutoPlace()),
      List.of(SEP, "§7Current: " + boolIcon(shop.getDisplayConfig() != null && shop.getDisplayConfig().isAutoPlace()), "",
        "§7When ON, products fill the grid automatically.",
        "§7When OFF, each product needs a slot number.", SEP,
        "§a▶ Click §7→ Toggle"),
      a -> {
        boolean auto = shop.getDisplayConfig() != null && shop.getDisplayConfig().isAutoPlace();
        shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().autoPlace(!auto).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    int rows = shop.getDisplayConfig() != null ? shop.getDisplayConfig().getRows() : 6;
    template.set(3, button(new ItemStack(Items.OAK_STAIRS, Math.max(1, rows)),
      "§eRows: §f" + rows,
      List.of(SEP, "§7Current: §f" + rows + " rows §8(" + (rows * 9) + " slots)", "",
        "§7Number of rows in the shop chest GUI.", SEP,
        "§a▶ Left §7→ +1", "§c▶ Right §7→ -1"),
      a -> {
        int r = shop.getDisplayConfig() != null ? shop.getDisplayConfig().getRows() : 6;
        if (a.getClickType().name().contains("LEFT")) r = Math.min(r + 1, 6);
        else r = Math.max(r - 1, 1);
        shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().rows(r).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    float globalDiscount = shop.getEconomyConfig() != null ? shop.getEconomyConfig().getGlobalDiscount() : 0f;
    template.set(4, button(new ItemStack(Items.GOLD_INGOT),
      "§e✦ Global Discount: §f" + globalDiscount + "%",
      List.of(SEP, "§7Current: §e" + globalDiscount + "%", "",
        "§7Applied to ALL buy prices in this shop.",
        "§7Stacks with per-product discounts.", SEP,
        "§a▶ Left §7→ +5%", "§c▶ Right §7→ -5%"),
      a -> {
        float gd = shop.getEconomyConfig() != null ? shop.getEconomyConfig().getGlobalDiscount() : 0f;
        if (a.getClickType().name().contains("LEFT")) gd = Math.min(gd + 5f, 100f);
        else gd = Math.max(gd - 5f, 0f);
        shop.setEconomyConfig(shop.getEconomyConfig().toBuilder().globalDiscount(gd).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));


    {
      var discounts = shop.getEconomyConfig() != null && shop.getEconomyConfig().getDiscounts() != null ? shop.getEconomyConfig().getDiscounts() : Map.<String, Float>of();
      List<String> discLore = new ArrayList<>();
      discLore.add(SEP);
      if (discounts.isEmpty()) {
        discLore.add("§7No permission discounts configured.");
      } else {
        for (var entry : discounts.entrySet()) {
          discLore.add("§7" + entry.getKey() + " §8→ §e" + entry.getValue() + "%");
        }
      }
      discLore.add(SEP);
      discLore.add("§8Edit in JSON: §7discounts");
      template.set(5, button(new ItemStack(Items.EXPERIENCE_BOTTLE),
        "§e✦ Permission Discounts §7(" + discounts.size() + ")",
        discLore, a -> {
        }));
    }


    {
      var economies = shop.getEconomyConfig() != null && shop.getEconomyConfig().getEconomies() != null ? shop.getEconomyConfig().getEconomies() : new LinkedHashSet<EconomyUse>();
      List<String> ecoLore = new ArrayList<>();
      ecoLore.add(SEP);
      for (var eco : economies) {
        ecoLore.add("§7• §f" + eco.getEconomyId() + " §8: §f" + eco.getCurrency());
      }
      ecoLore.add(SEP);
      ecoLore.add("§8Edit in JSON: §7economies");
      template.set(6, button(new ItemStack(Items.DIAMOND),
        "§e⛃ Economies §7(" + economies.size() + ")",
        ecoLore, a -> {
        }));
    }


    String soundOpen = shop.getSoundConfig() != null ? shop.getSoundConfig().getSoundOpen() : "";
    template.set(9, button(new ItemStack(Items.NOTE_BLOCK), "§e♪ Sound Open",
      List.of(SEP, "§7Current: §f" + orEmpty(soundOpen), "",
        "§7Sound played when the shop GUI opens.",
        "§7Example: §fminecraft:block.chest.open", SEP,
        "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter open sound (e.g. minecraft:block.chest.open):", input -> {
        shop.setSoundConfig(shop.getSoundConfig().toBuilder().soundOpen(input).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    String soundClose = shop.getSoundConfig() != null ? shop.getSoundConfig().getSoundClose() : "";
    template.set(10, button(new ItemStack(Items.NOTE_BLOCK), "§e♪ Sound Close",
      List.of(SEP, "§7Current: §f" + orEmpty(soundClose), "",
        "§7Sound played when the shop GUI closes.", SEP,
        "§a▶ Click §7→ Set via chat"),
      a -> ChatInputManager.requestInput(player, "Enter close sound:", input -> {
        shop.setSoundConfig(shop.getSoundConfig().toBuilder().soundClose(input).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
      })));

    String closeCommand = shop.getConditionsConfig() != null ? shop.getConditionsConfig().getCloseCommand() : "";
    template.set(11, button(new ItemStack(Items.LEVER), "§e⚙ Close Command",
      List.of(SEP, "§7Current: §f" + orEmpty(closeCommand), "",
        "§7Command that runs when close button is clicked.",
        "§7Use §f%player% §7for the player's name.", SEP,
        "§a▶ Click §7→ Set", "§c▶ Shift §7→ Clear"),
      a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().closeCommand("").build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        } else ChatInputManager.requestInput(player, "Enter close command:", input -> {
          shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().closeCommand(input).build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
        });
      }));

    boolean announceRot = shop.getConditionsConfig() != null && shop.getConditionsConfig().isAnnounceRotation();
    template.set(12, button(new ItemStack(announceRot ? Items.BELL : Items.GRAY_DYE),
      "§e📢 Announce Rotation: " + boolIcon(announceRot),
      List.of(SEP, "§7Current: " + boolIcon(announceRot), "",
        "§7Broadcasts a message to all players when",
        "§7the shop's products rotate.", SEP,
        "§a▶ Click §7→ Toggle"),
      a -> {
        shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().announceRotation(!announceRot).build());
        ctx.replaceShop(modId, shop);
        ConfigLoader.saveShop(shop);
        openShopSettings(player, shop, config, modId);
      }));

    String colorProduct = shop.getDisplayConfig() != null ? shop.getDisplayConfig().getColorProduct() : "";
    template.set(13, button(new ItemStack(Items.SPYGLASS), "§e🎨 Color Prefix",
      List.of(SEP, "§7Current: §f" + orEmpty(colorProduct), "",
        "§7Color prefix added to product names.",
        "§7Example: §6&6 §7→ gold text", SEP,
        "§a▶ Click §7→ Set", "§c▶ Shift §7→ Clear"),
      a -> {
        if (a.getClickType().name().contains("SHIFT")) {
          shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().colorProduct("").build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        } else ChatInputManager.requestInput(player, "Enter color prefix (e.g. &6, <#ff0000>):", input -> {
          shop.setDisplayConfig(shop.getDisplayConfig().toBuilder().colorProduct(input).build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
        });
      }));


    {
      var openConditions = shop.getConditionsConfig() != null && shop.getConditionsConfig().getOpenConditions() != null ? shop.getConditionsConfig().getOpenConditions() : List.<Condition>of();
      List<String> condLore = new ArrayList<>();
      condLore.add(SEP);
      if (openConditions.isEmpty()) {
        condLore.add("§7No conditions — shop is always open.");
      } else {
        for (int i = 0; i < openConditions.size(); i++) {
          var cond = openConditions.get(i);
          condLore.add("§7" + (i + 1) + ". §f" + cond.getType());
        }
      }
      condLore.add(SEP);
      condLore.add("§a▶ Click §7→ Manage conditions");
      template.set(14, button(new ItemStack(Items.IRON_BARS),
        "§c⚡ Open Conditions §7(" + openConditions.size() + ")",
        condLore, a -> openConditionsList(player, shop, config, modId)));
    }


    {
      String webhook = shop.getWebhookUrl() != null ? shop.getWebhookUrl() : "";
      template.set(7, button(new ItemStack(Items.WRITABLE_BOOK), "§e⚙ Webhook URL",
        List.of(SEP, "§7Current: §f" + truncate(webhook, 45), "",
          "§7Discord webhook URL for notifications.", SEP,
          "§a▶ Click §7→ Set via chat", "§c▶ Shift §7→ Clear"),
        a -> {
          if (a.getClickType().name().contains("SHIFT")) {
            shop.setWebhookUrl("");
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
            openShopSettings(player, shop, config, modId);
          } else {
            ChatInputManager.requestInput(player, "Enter webhook URL:", input -> {
              shop.setWebhookUrl(input);
              ctx.replaceShop(modId, shop);
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
            });
          }
        }));
    }


    {
      boolean maintenance = shop.isMaintenance();
      template.set(8, button(new ItemStack(maintenance ? Items.REDSTONE_TORCH : Items.LEVER),
        "§e⚙ Maintenance Mode: " + boolIcon(maintenance),
        List.of(SEP, "§7Current: " + boolIcon(maintenance), "",
          "§7When ON, players cannot open this shop.", SEP,
          "§a▶ Click §7→ Toggle"),
        a -> {
          shop.setMaintenance(!maintenance);
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        }));
    }


    {
      ItemModel displayItem = shop.getDisplayConfig() != null ? shop.getDisplayConfig().getDisplayItem() : null;
      String displayStr = displayItem != null ? displayItem.getItem() : "minecraft:book";
      template.set(15, button(new ItemStack(displayItem != null ? displayItem.getItemStack().getItem() : Items.BOOK),
        "§e🎨 Display Item (Icon)",
        List.of(SEP, "§7Current: §f" + displayStr, "",
          "§7Icon representing this shop in menus.", SEP,
          "§a▶ Click §7→ Set to item in hand", "§e▶ Right Click §7→ Set via chat"),
        a -> {
          switch (a.getClickType()) {
            case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> ChatInputManager.requestInput(player, "Enter display item ID (e.g. minecraft:diamond):", input -> {
              ItemModel newItem = new ItemModel(input);
              DisplayConfig dc = shop.getDisplayConfig() != null ? shop.getDisplayConfig() : DisplayConfig.builder().build();
              shop.setDisplayConfig(dc.toBuilder().displayItem(newItem).build());
              ctx.replaceShop(modId, shop);
              ConfigLoader.saveShop(shop);
              ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
            });
            default -> {
              ItemStack hand = player.getMainHandStack();
              if (!hand.isEmpty()) {
                String itemId = itemStackToProductId(hand);
                ItemModel newItem = new ItemModel(itemId);
                DisplayConfig dc = shop.getDisplayConfig() != null ? shop.getDisplayConfig() : DisplayConfig.builder().build();
                shop.setDisplayConfig(dc.toBuilder().displayItem(newItem).build());
                ctx.replaceShop(modId, shop);
                ConfigLoader.saveShop(shop);
                openShopSettings(player, shop, config, modId);
              } else {
                PlayerUtils.sendMessage(player, "§cHold an item in your hand first.", lang.getPrefix(), TypeMessage.CHAT);
              }
            }
          }
        }));
    }


    {
      List<String> rotLore = new ArrayList<>();
      rotLore.add(SEP);
      rotLore.add("§7Type: §f" + shop.getType());
      if (shop instanceof RotationShop r) {
        Scheduler scheduler = r.getScheduler();
        if (scheduler instanceof CronScheduler cron) {
          rotLore.add("§7Cron: §f" + cron.getExpression() + " §8(priority)");
          rotLore.add("§7Interval: none §8(ignored — cron set)");
        } else if (scheduler instanceof DurationScheduler dur) {
          rotLore.add("§7Cron: §8none");
          rotLore.add("§7Interval: §f" + dur.getDuration());
        }
        rotLore.add("§7Amount: §f" + r.getRotationAmount() + " products per rotation");
        long next = ShopContext.get().getDataShop().getActualCooldown(modId, shop.getId());
        if (next > 0) {
          rotLore.add("§7Next rotation: §f" + java.time.Instant.ofEpochMilli(next));
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
              if (shop instanceof RotationShop r) {
                NormalShop normal = new NormalShop();
                normal.setId(r.getId());
                normal.setFilePath(r.getFilePath());
                normal.setDisplayConfig(r.getDisplayConfig());
                normal.setEconomyConfig(r.getEconomyConfig());
                normal.setConditionsConfig(r.getConditionsConfig());
                normal.setSoundConfig(r.getSoundConfig());
                normal.setMaintenance(r.isMaintenance());
                normal.setWebhookUrl(r.getWebhookUrl());
                normal.setProducts(new ArrayList<>(r.getProductPool()));
                ctx.replaceShop(modId, normal);
                ConfigLoader.saveShop(normal);
                openShopSettings(player, normal, config, modId);
              }
            }
            case RIGHT_CLICK -> ChatInputManager.requestInput(player, "Enter rotation amount:", input -> {
              try {
                int amt = Integer.parseInt(input);
                if (shop instanceof RotationShop r) {
                  r.setRotationAmount(amt);
                  ctx.replaceShop(modId, r);
                  ConfigLoader.saveShop(r);
                  ctx.runOnServer(() -> openShopSettings(player, r, config, modId));
                } else {
                  RotationShop rotation = new RotationShop();
                  rotation.setId(shop.getId());
                  rotation.setFilePath(shop.getFilePath());
                  rotation.setDisplayConfig(shop.getDisplayConfig());
                  rotation.setEconomyConfig(shop.getEconomyConfig());
                  rotation.setConditionsConfig(shop.getConditionsConfig());
                  rotation.setSoundConfig(shop.getSoundConfig());
                  rotation.setMaintenance(shop.isMaintenance());
                  rotation.setWebhookUrl(shop.getWebhookUrl());
                  if (shop instanceof NormalShop n) {
                    rotation.setProductPool(new ArrayList<>(n.getProducts()));
                  }
                  rotation.setRotationAmount(amt);
                  rotation.setScheduler(Scheduler.defaultScheduler());
                  ctx.replaceShop(modId, rotation);
                  ConfigLoader.saveShop(rotation);
                  ctx.runOnServer(() -> openShopSettings(player, rotation, config, modId));
                }
              } catch (NumberFormatException e) {
                sendConfiguredMessage(player, lang.getMessageInvalidNumber().replace("%input%", input));
              }
            });
            case MIDDLE_CLICK -> ChatInputManager.requestInput(player, "Enter cron (e.g. 0 18 * * 5):", input -> {
              try {
                CronExpression.parse(input);
              } catch (Exception e) {
                sendConfiguredMessage(player, lang.getMessageConditionCreateFailed().replace("%error%", e.getMessage()));
                return;
              }
              if (shop instanceof RotationShop r) {
                r.setScheduler(new CronScheduler(input));
                ctx.replaceShop(modId, r);
                ConfigLoader.saveShop(r);
                ctx.runOnServer(() -> openShopSettings(player, r, config, modId));
              } else {
                RotationShop rotation = new RotationShop();
                rotation.setId(shop.getId());
                rotation.setFilePath(shop.getFilePath());
                rotation.setDisplayConfig(shop.getDisplayConfig());
                rotation.setEconomyConfig(shop.getEconomyConfig());
                rotation.setConditionsConfig(shop.getConditionsConfig());
                rotation.setSoundConfig(shop.getSoundConfig());
                rotation.setMaintenance(shop.isMaintenance());
                rotation.setWebhookUrl(shop.getWebhookUrl());
                if (shop instanceof NormalShop n) {
                  rotation.setProductPool(new ArrayList<>(n.getProducts()));
                }
                rotation.setRotationAmount(3);
                rotation.setScheduler(new CronScheduler(input));
                ctx.replaceShop(modId, rotation);
                ConfigLoader.saveShop(rotation);
                ctx.runOnServer(() -> openShopSettings(player, rotation, config, modId));
              }
            });
            default -> ChatInputManager.requestInput(player, "Enter interval (e.g. 30m, 1h, 12h, 7d):", input -> {
              try {
                new DurationScheduler(input);
              } catch (Exception e) {
                sendConfiguredMessage(player, lang.getMessageConditionCreateFailed().replace("%error%", e.getMessage()));
                return;
              }
              if (shop instanceof RotationShop r) {
                r.setScheduler(new DurationScheduler(input));
                ctx.replaceShop(modId, r);
                ConfigLoader.saveShop(r);
                ctx.runOnServer(() -> openShopSettings(player, r, config, modId));
              } else {
                RotationShop rotation = new RotationShop();
                rotation.setId(shop.getId());
                rotation.setFilePath(shop.getFilePath());
                rotation.setDisplayConfig(shop.getDisplayConfig());
                rotation.setEconomyConfig(shop.getEconomyConfig());
                rotation.setConditionsConfig(shop.getConditionsConfig());
                rotation.setSoundConfig(shop.getSoundConfig());
                rotation.setMaintenance(shop.isMaintenance());
                rotation.setWebhookUrl(shop.getWebhookUrl());
                if (shop instanceof NormalShop n) {
                  rotation.setProductPool(new ArrayList<>(n.getProducts()));
                }
                rotation.setRotationAmount(3);
                rotation.setScheduler(new DurationScheduler(input));
                ctx.replaceShop(modId, rotation);
                ConfigLoader.saveShop(rotation);
                ctx.runOnServer(() -> openShopSettings(player, rotation, config, modId));
              }
            });
          }
        }));
    }


    template.set(27, backBtn(lang, a -> openShopList(player, config, modId)));

    {
      String dailyCooldown = absShop.getDailySellResetCooldown() != null ? absShop.getDailySellResetCooldown() : "24h";
      template.set(19, button(new ItemStack(Items.CLOCK), "§e⚙ Daily Sell Reset Cooldown",
        List.of(SEP,
          "§7Current: §f" + dailyCooldown,
          "",
          "§7Time or cron expression before daily sell limits reset.",
          "§7Examples: §f24h§7, §f12h§7, or §f0 0 * * * §7(midnight).",
          SEP,
          "§a▶ Click §7→ Set via chat"),
        a -> ChatInputManager.requestInput(player, "Enter daily sell reset cooldown (e.g. 24h, 12h, 0 0 * * *):", input -> {
          if (input != null && !input.isBlank()) {
            absShop.setDailySellResetCooldown(input.trim());
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
          }
          ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
        })));
    }

    {
      var dailyLimits = absShop.getDailySellLimits() != null ? absShop.getDailySellLimits() : Map.<String, BigDecimal>of();
      List<String> limitsLore = new ArrayList<>();
      limitsLore.add(SEP);
      if (dailyLimits.isEmpty()) {
        limitsLore.add("§7No daily sell limits configured.");
      } else {
        for (var entry : dailyLimits.entrySet()) {
          limitsLore.add("§7• §f" + entry.getKey() + " §8→ §a" + fmt(entry.getValue()));
        }
      }
      limitsLore.add("");
      limitsLore.add("§7Configure max total sales per player per day.");
      limitsLore.add("§7Supports any configured economy/currency.");
      limitsLore.add(SEP);
      limitsLore.add("§a▶ Click §7→ Add/Update limit via chat");
      limitsLore.add("§c▶ Shift+Click §7→ Clear all limits");

      template.set(20, button(new ItemStack(Items.CHEST_MINECART), "§e⚙ Daily Sell Limits §7(" + dailyLimits.size() + ")",
        limitsLore,
        a -> {
          if (a.getClickType().name().contains("SHIFT")) {
            if (absShop.getDailySellLimits() != null) {
              absShop.getDailySellLimits().clear();
            }
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
            openShopSettings(player, shop, config, modId);
          } else {
            ChatInputManager.requestInput(player, "Enter limit (format: economy:limit or economy:currency:limit, e.g. dollars:5000):", input -> {
              if (input != null && !input.isBlank()) {
                String[] parts = input.split(":");
                if (parts.length >= 2) {
                  try {
                    String limitStr = parts[parts.length - 1].trim();
                    BigDecimal limitVal = new BigDecimal(limitStr);
                    StringBuilder keyBuilder = new StringBuilder();
                    for (int idx = 0; idx < parts.length - 1; idx++) {
                      if (idx > 0) keyBuilder.append(":");
                      keyBuilder.append(parts[idx].trim());
                    }
                    String limitKey = keyBuilder.toString();
                    if (!limitKey.isEmpty()) {
                      if (absShop.getDailySellLimits() == null) {
                        absShop.setDailySellLimits(new HashMap<>());
                      }
                      absShop.getDailySellLimits().put(limitKey, limitVal);
                      ctx.replaceShop(modId, shop);
                      ConfigLoader.saveShop(shop);
                    }
                  } catch (Exception e) {
                    PlayerUtils.sendMessage(player, "§cInvalid format or number: " + input, lang.getPrefix(), TypeMessage.CHAT);
                  }
                } else {
                  PlayerUtils.sendMessage(player, "§cInvalid format. Use key:value.", lang.getPrefix(), TypeMessage.CHAT);
                }
              }
              ctx.runOnServer(() -> openShopSettings(player, shop, config, modId));
            });
          }
        }));
    }

    template.set(31, closeBtn(lang, player));

    GooeyPage page = GooeyPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleShopSettings().replace("%shop%", shop.getId())))
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  /**
   * Opens a GUI showing all unique items in the player's inventory.
   * Clicking an item adds it as a new product to the shop.
   */
  public static void openInventoryPicker(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();


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
          List<Product> products = getEditableProducts(shop);
          products.add(p);
          ctx.replaceShop(modId, shop);
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
      .title(AdventureTranslator.toNative(lang.getEditorTitleInventoryPicker()));
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
      .title(AdventureTranslator.toNative(lang.getEditorTitleInventoryPickerEdit().replace("%product%", truncate(product.getProduct(), 18))));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  /**
   * Opens a menu listing the shop's current open conditions.
   * Players can add new conditions (with defaults) or remove existing ones.
   * Fine-tuning values should be done in the JSON file.
   */
  public static void openConditionsList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Button> buttons = new ArrayList<>();
    var openConditions = shop.getConditionsConfig() != null && shop.getConditionsConfig().getOpenConditions() != null
        ? shop.getConditionsConfig().getOpenConditions() : List.<Condition>of();

    for (int i = 0; i < openConditions.size(); i++) {
      var cond = openConditions.get(i);
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
          List<Condition> mutableConditions = new ArrayList<>(openConditions);
          mutableConditions.remove(idx);
          shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().openConditions(mutableConditions).build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openConditionsList(player, shop, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openShopSettings(player, shop, config, modId)));


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
      .title(AdventureTranslator.toNative(lang.getEditorTitleConditionList().replace("%shop%", shop.getId())));
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


    Map<String, Class<? extends Condition>> types = getConditionTypes();
    if (types == null || types.isEmpty()) {
      sendConfiguredMessage(player, lang.getMessageConditionTypesUnavailable());
      openConditionsList(player, shop, config, modId);
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
      lore.add("§a▶ Click §7→ Add to shop");

      buttons.add(button(new ItemStack(Items.CHAIN), "§e" + typeName, lore, a -> {
        try {
          var condition = clazz.getDeclaredConstructor().newInstance();
          var openConditions = shop.getConditionsConfig() != null && shop.getConditionsConfig().getOpenConditions() != null
              ? shop.getConditionsConfig().getOpenConditions() : List.<Condition>of();
          List<Condition> mutableConditions = new ArrayList<>(openConditions);
          mutableConditions.add(condition);
          shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().openConditions(mutableConditions).build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openConditionsList(player, shop, config, modId);
        } catch (Exception e) {
          sendConfiguredMessage(player, lang.getMessageConditionCreateFailed().replace("%error%", e.getMessage()));
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openConditionsList(player, shop, config, modId)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleAddCondition()));
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
      var field = ConditionAdapter.class.getDeclaredField("TYPES");
      field.setAccessible(true);
      return (Map<String, Class<? extends Condition>>) field.get(null);
    } catch (Exception e) {
      return null;
    }
  }



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
      sendConfiguredMessage(player, lang.getMessageConditionTypesUnavailable());
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
          sendConfiguredMessage(player, lang.getMessageConditionCreateFailed().replace("%error%", e.getMessage()));
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> openProductConditionsList(player, shop, product, config, modId, isVisibility)));
    template.set(49, closeBtn(lang, player));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleAddCondition()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }



  private static GooeyButton button(ItemStack icon, String name, List<String> lore,
                                    Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(icon)
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
      .onClick(onClick::accept)
      .build();
  }

  private static GooeyButton label(Item item, String name) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .build();
  }

  private static GooeyButton label(Item item, String name, List<String> lore) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .build();
  }

  private static GooeyButton priceBtn(String label, Supplier<BigDecimal> getter,
                                      Consumer<BigDecimal> setter, int delta,
                                      Shop shop, ServerPlayerEntity player, Product product,
                                      ShopConfig config, String modId) {
    Item item = delta > 0 ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
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
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(lang.getEditorButtonClose()))
      .onClick(a -> UIManager.closeUI(player))
      .build();
  }

  private static GooeyButton backBtn(LangConfig lang, Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(lang.getGlobalItemPrevious().getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(lang.getEditorButtonBack()))
      .onClick(onClick::accept)
      .build();
  }

  private static void sendConfiguredMessage(ServerPlayerEntity player, String message) {
    LangConfig lang = ShopContext.get().getLang();
    PlayerUtils.sendMessage(player,
      message.replace("%prefix%", lang.getPrefix()),
      lang.getPrefix(), TypeMessage.CHAT);
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


      var registryOps = com.kingpixel.cobbleutils.CobbleUtils.server
        .getRegistryManager().getOps(net.minecraft.nbt.NbtOps.INSTANCE);

      var nbtElement = net.minecraft.item.ItemStack.CODEC
        .encodeStart(registryOps, stack)
        .getOrThrow();

      if (nbtElement instanceof net.minecraft.nbt.NbtCompound compound && compound.contains("components")) {
        var components = compound.getCompound("components");
        if (components != null && !components.isEmpty()) {



          StringBuilder sb = new StringBuilder();
          sb.append("[");
          boolean first = true;
          for (String key : components.getKeys()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(key).append("=").append(components.get(key).toString());
          }
          sb.append("]");

          return "item:1:" + itemId + "#" + sb;
        }
      }
    } catch (Exception e) {

    }

    return itemId;
  }

  private static String addMinutesToCooldown(String cooldown, int minutesToAdd) {
    if (cooldown == null || cooldown.isBlank()) {
      int newMinutes = Math.max(0, minutesToAdd);
      return newMinutes + "m";
    }
    cooldown = cooldown.trim();
    if (cooldown.contains(" ") || cooldown.contains("*")) {

      return cooldown;
    }


    int totalMinutes = 0;
    try {
      if (cooldown.matches("\\d+")) {
        totalMinutes = Integer.parseInt(cooldown);
      } else {

        Matcher m = Pattern.compile("(\\d+)([smdh])").matcher(cooldown.toLowerCase());
        while (m.find()) {
          int val = Integer.parseInt(m.group(1));
          String unit = m.group(2);
          switch (unit) {
            case "s" -> totalMinutes += val / 60;
            case "m" -> totalMinutes += val;
            case "h" -> totalMinutes += val * 60;
            case "d" -> totalMinutes += val * 1440;
          }
        }
        if (totalMinutes == 0) {
          totalMinutes = Integer.parseInt(cooldown.replaceAll("[^0-9]", ""));
        }
      }
    } catch (Exception e) {
      totalMinutes = 60;
    }

    int newMinutes = Math.max(0, totalMinutes + minutesToAdd);
    return newMinutes + "m";
  }
}
