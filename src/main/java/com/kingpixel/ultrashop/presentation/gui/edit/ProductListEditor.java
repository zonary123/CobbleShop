package com.kingpixel.ultrashop.presentation.gui.edit;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.ui.PartyPcMenu;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.kingpixel.ultrashop.presentation.gui.edit.EditorHelpers.*;

public final class ProductListEditor {

  private ProductListEditor() {}

  public static void openProductList(ServerPlayerEntity player, Shop shop, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    List<Product> products = getEditableProducts(shop);
    List<Button> buttons = new ArrayList<>();

    for (Product product : products) {
      List<String> lore = new ArrayList<>();
      lore.add(SEP);
      lore.add("§7Product ID: §f" + truncate(product.getProduct(), 32));
      lore.add("§7Type: " + productTypeTag(product));

      if (product.getDisplayname() != null) {
        lore.add("§7Display Name: §f" + product.getDisplayname());
      }
      lore.add("");
      lore.add("§a✦ Pricing");
      lore.add("  §7Buy Price: §e" + (product.getBuy() != null ? fmt(product.getBuy()) : "§8disabled"));
      lore.add("  §7Sell Price: §e" + (product.getSell() != null ? fmt(product.getSell()) : "§8disabled"));

      if (product.getStockAmount() != null) {
        lore.add("  §7Stock: §c" + product.getStockAmount() + " §8(" + product.getStockMode() + ")");
      }
      if (product.getSellMax() != null) {
        lore.add("  §7Sell Limit: §c" + product.getSellMax() + " §8(cooldown: " + product.getSellCooldown() + ")");
      }
      if (product.getConditions() != null && !product.getConditions().isEmpty()) {
        lore.add("  §7Buy Conditions: §f" + product.getConditions().size());
      }
      if (product.getVisibilityConditions() != null && !product.getVisibilityConditions().isEmpty()) {
        lore.add("  §7Visibility Conditions: §f" + product.getVisibilityConditions().size());
      }

      lore.add(SEP);
      lore.add("§a▶ Left click §7→ Edit product details");
      lore.add("§c▶ Shift+Right click §7→ Delete product");

      ItemStack icon = product.getItemStack();
      if (icon.isEmpty()) {
        icon = new ItemStack(Items.BARRIER);
      }

      buttons.add(button(icon, "§b" + (product.getDisplayname() != null ? product.getDisplayname() : getCleanNameFromId(product.getProduct())), lore, action -> {
        switch (action.getClickType()) {
          case SHIFT_RIGHT_CLICK -> {
            products.remove(product);
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);
            openProductList(player, shop, config, modId);
          }
          default -> ProductEditor.openProductEditor(player, shop, product, config, modId);
        }
      }));
    }

    ChestTemplate template = ChestTemplate.builder(6).build();
    template.set(45, backBtn(lang, a -> ShopListEditor.openShopList(player, config, modId)));

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
      "§7You can choose between typing it in chat",
      "§7with suggestions, or selecting it directly",
      "§7from your Party or PC.",
      "",
      "§7Default: §aBuy 1000 §7/ §cSell 0 §7/ OneByOne",
      SEP,
      "§a▶ Click §7→ Choose option"
    ), a -> openPokemonAddOptions(player, shop, products, config, modId)));

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

    template.set(49, backBtn(lang, a -> ShopListEditor.openShopList(player, config, modId)));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleProductList().replace("%shop%", shop.getId())));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

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
    template.set(49, backBtn(lang, a -> openProductList(player, shop, config, modId)));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleInventoryPicker()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  public static void openPokemonAddOptions(ServerPlayerEntity player, Shop shop, List<Product> products,
                                                 ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();
    LangConfig lang = ctx.getLang();
    ChestTemplate template = ChestTemplate.builder(3).build();

    template.set(11, button(new ItemStack(Items.PAPER), "§b✎ Type Showdown Format", List.of(
      SEP,
      "§7Enter Pokemon parameters in chat.",
      "§7Uses CobbleUtils syntax.",
      "",
      "§7Examples:",
      "§f  pikachu level=50 shiny=yes",
      "§f  charizard level=100 nature=jolly",
      SEP,
      "§a▶ Click §7→ Type in chat"
    ), a -> ChatInputManager.requestInput(player, "Enter Pokémon spec (e.g. pikachu level=50 shiny=yes):", input -> {
      Product p = new Product();
      p.setProduct("pokemon:" + input.trim());
      p.setBuy(BigDecimal.valueOf(1000));
      p.setSell(BigDecimal.ZERO);
      p.setOneByOne(true);
      products.add(p);
      ctx.replaceShop(modId, shop);
      ConfigLoader.saveShop(shop);
      ctx.runOnServer(() -> openProductList(player, shop, config, modId));
    })));

    template.set(15, button(new ItemStack(Items.PLAYER_HEAD), "§dSelect from Party / PC", List.of(
      SEP,
      "§7Opens your party menu.",
      "§7Click any Pokemon to add it",
      "§7as a product in this shop.",
      SEP,
      "§a▶ Click §7→ Open party selector"
    ), a -> {
      try {
        var builder = PartyPcMenu.builder()
          .setPlayer(player)
          .setPokemonAction(pokemonAction -> {
            var pokemon = pokemonAction.getPokemon();
            String propertiesStr = getPokemonPropertiesString(pokemon);
            Product p = new Product();
            p.setProduct("pokemon:" + propertiesStr);
            p.setBuy(BigDecimal.valueOf(1000));
            p.setSell(BigDecimal.ZERO);
            p.setOneByOne(true);
            products.add(p);
            ctx.replaceShop(modId, shop);
            ConfigLoader.saveShop(shop);

            player.sendMessage(net.minecraft.text.Text.literal("§aAdded Pokémon from Party/PC: pokemon:" + propertiesStr));
            ctx.runOnServer(() -> openProductList(player, shop, config, modId));
          })
          .setCloseAction(closeAction -> {
            ctx.runOnServer(() -> openProductList(player, shop, config, modId));
          })
          .build();
        PartyPcMenu.openDefaultParty(builder);
      } catch (Exception e) {
        player.sendMessage(net.minecraft.text.Text.literal("§cError opening Party/PC menu: " + e.getMessage()));
        ctx.runOnServer(() -> openProductList(player, shop, config, modId));
      }
    }));

    template.set(22, backBtn(lang, a -> openProductList(player, shop, config, modId)));

    GooeyPage page = GooeyPage.builder()
      .title(AdventureTranslator.toNative("§bAdd Pokémon Options"))
      .template(template)
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  static List<Product> getEditableProducts(Shop shop) {
    if (shop instanceof NormalShop normal) {
      return normal.getProducts();
    } else if (shop instanceof RotationShop rotation) {
      return rotation.getProducts();
    }
    return new ArrayList<>();
  }

  static String getCleanNameFromId(String id) {
    if (id == null) return "Unknown";
    String clean = id;
    if (clean.startsWith("item:1:")) clean = clean.substring(7);
    if (clean.startsWith("pokemon:")) clean = clean.substring(8);
    if (clean.startsWith("command:")) clean = clean.substring(8);
    int hashIdx = clean.indexOf('#');
    if (hashIdx != -1) clean = clean.substring(0, hashIdx);
    String[] parts = clean.split(":");
    String last = parts[parts.length - 1];
    return last.replace('_', ' ');
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
}
