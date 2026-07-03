package com.kingpixel.ultrashop.presentation.gui.edit;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.RotationScope;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

import static com.kingpixel.ultrashop.presentation.gui.edit.EditorHelpers.*;

public final class ShopListEditor {

  private ShopListEditor() {}

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
        productCount = rotation.getProducts() != null ? rotation.getProducts().size() : 0;
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
        lore.add("  §7Scope: §f" + (r.getRotationScope() != null ? r.getRotationScope() : RotationScope.GLOBAL));
        if (r.getRotationSlots() != null && !r.getRotationSlots().isEmpty()) {
          lore.add("  §7Slots: §f" + r.getRotationSlots());
        }
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
          case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> ShopSettingsEditor.openShopSettings(player, shop, config, modId);
          default -> ProductListEditor.openProductList(player, shop, config, modId);
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
    LinkedPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }
}
