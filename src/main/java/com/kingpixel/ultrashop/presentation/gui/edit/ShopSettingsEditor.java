package com.kingpixel.ultrashop.presentation.gui.edit;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.Model.conditions.Condition;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.CronExpression;
import com.kingpixel.cobbleutils.adapter.ConditionAdapter;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.RotationScope;
import com.kingpixel.ultrashop.domain.model.shop.*;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.*;

import static com.kingpixel.ultrashop.presentation.gui.edit.EditorHelpers.*;

public final class ShopSettingsEditor {

  private ShopSettingsEditor() {}

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
    if (shop instanceof RotationShop rotationForAnnounce) {
      boolean playerScoped = rotationForAnnounce.isPlayerScoped();
      template.set(12, button(new ItemStack(announceRot ? Items.BELL : Items.GRAY_DYE),
        "§e📢 Announce Rotation: " + boolIcon(announceRot),
        List.of(SEP, "§7Current: " + boolIcon(announceRot), "",
          playerScoped
            ? "§7Notifies §fthis player §7when their personal catalog rotates."
            : "§7Broadcasts a message to all players when the shop rotates.",
          SEP,
          "§a▶ Click §7→ Toggle"),
        a -> {
          shop.setConditionsConfig(shop.getConditionsConfig().toBuilder().announceRotation(!announceRot).build());
          ctx.replaceShop(modId, shop);
          ConfigLoader.saveShop(shop);
          openShopSettings(player, shop, config, modId);
        }));
    }

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
            case RIGHT_CLICK, SHIFT_RIGHT_CLICK ->
              ChatInputManager.requestInput(player, "Enter display item ID (e.g. minecraft:diamond):", input -> {
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

    if (shop instanceof NormalShop || shop instanceof RotationShop) {
      List<String> rotLore = new ArrayList<>();
      rotLore.add(SEP);
      rotLore.add("§7Type: §f" + shop.getType());
      if (shop instanceof RotationShop r) {
        rotLore.add("§7Scope: §f" + (r.getRotationScope() != null ? r.getRotationScope() : RotationScope.GLOBAL));
        Scheduler scheduler = r.getScheduler();
        if (scheduler instanceof CronScheduler cron) {
          rotLore.add("§7Cron: §f" + cron.getExpression() + " §8(priority)");
          rotLore.add("§7Interval: none §8(ignored — cron set)");
        } else if (scheduler instanceof DurationScheduler dur) {
          rotLore.add("§7Cron: §8none");
          rotLore.add("§7Interval: §f" + dur.getDuration());
        }
        rotLore.add("§7Amount: §f" + r.getRotationAmount() + " products per rotation");
        if (r.getRotationSlots() != null && !r.getRotationSlots().isEmpty()) {
          rotLore.add("§7Slots: §f" + r.getRotationSlots());
        } else {
          rotLore.add("§7Slots: §8auto / product slot");
        }
        long next = shop instanceof RotationShop rs && rs.getRotationScope() != RotationScope.PLAYER && rs.getRotationScope() != RotationScope.GUILD
          ? ShopContext.get().getDataShop().getActualCooldown(modId, shop.getId())
          : 0L;
        if (next > 0) {
          rotLore.add("§7Next rotation: §f" + java.time.Instant.ofEpochMilli(next));
        } else if (shop instanceof RotationShop rs && rs.getRotationScope() == RotationScope.PLAYER) {
          rotLore.add("§7Next rotation: §8per player");
        } else if (shop instanceof RotationShop rs && rs.getRotationScope() == RotationScope.GUILD) {
          rotLore.add("§7Next rotation: §8per guild");
        }
      } else {
        rotLore.add("§7Not a rotation shop yet.");
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
                normal.setProducts(new ArrayList<>(r.getProducts()));
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
                } else if (shop instanceof NormalShop n) {
                  RotationShop rotation = newRotationFrom(shop, n.getProducts());
                  rotation.setRotationAmount(amt);
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
              } else if (shop instanceof NormalShop n) {
                RotationShop rotation = newRotationFrom(shop, n.getProducts());
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
              } else if (shop instanceof NormalShop n) {
                RotationShop rotation = newRotationFrom(shop, n.getProducts());
                rotation.setScheduler(new DurationScheduler(input));
                ctx.replaceShop(modId, rotation);
                ConfigLoader.saveShop(rotation);
                ctx.runOnServer(() -> openShopSettings(player, rotation, config, modId));
              }
            });
          }
        }));
    }

    if (shop instanceof RotationShop rotationShop) {
      RotationScope scope = rotationShop.getRotationScope() != null
        ? rotationShop.getRotationScope() : RotationScope.GLOBAL;
      Item displayItem;
      if (scope == RotationScope.GLOBAL) {
        displayItem = Items.CLOCK;
      } else if (scope == RotationScope.PLAYER) {
        displayItem = Items.PLAYER_HEAD;
      } else {
        displayItem = Items.WRITABLE_BOOK;
      }
      template.set(16, button(new ItemStack(displayItem),
        "§b⟳ Rotation Scope: §f" + scope,
        List.of(SEP,
          "§7Current: §f" + scope,
          "",
          "§fGLOBAL §7— everyone sees the same rotating catalog.",
          "§fPLAYER §7— each player has their own rotation timer.",
          "§fGUILD  §7— shared rotation catalog per guild.",
          SEP,
          "§a▶ Click §7→ Cycle scope (GLOBAL -> PLAYER -> GUILD)"),
        a -> {
          RotationScope nextScope;
          if (scope == RotationScope.GLOBAL) nextScope = RotationScope.PLAYER;
          else if (scope == RotationScope.PLAYER) nextScope = RotationScope.GUILD;
          else nextScope = RotationScope.GLOBAL;

          rotationShop.setRotationScope(nextScope);
          ctx.replaceShop(modId, rotationShop);
          ConfigLoader.saveShop(rotationShop);
          openShopSettings(player, rotationShop, config, modId);
        }));

      int maxSlot = rows * 9 - 1;
      List<Integer> currentSlots = rotationShop.getRotationSlots() != null
        ? rotationShop.getRotationSlots() : List.of();
      List<String> slotsLore = new ArrayList<>();
      slotsLore.add(SEP);
      if (currentSlots.isEmpty()) {
        slotsLore.add("§7No fixed slots — uses AutoPlace or per-product slots.");
      } else {
        slotsLore.add("§7Fixed Slots: §f" + currentSlots);
      }
      slotsLore.add(SEP);
      slotsLore.add("§a▶ Click §7→ Set fixed slots via chat");
      slotsLore.add("§c▶ Shift §7→ Clear fixed slots");

      template.set(22, button(new ItemStack(Items.CHEST), "§b⟳ Fixed Rotation Slots", slotsLore,
        a -> {
          if (a.getClickType().name().contains("SHIFT")) {
            rotationShop.getRotationSlots().clear();
            ctx.replaceShop(modId, rotationShop);
            ConfigLoader.saveShop(rotationShop);
            openShopSettings(player, rotationShop, config, modId);
          } else {
            ChatInputManager.requestInput(player, "Enter fixed slots separated by commas (e.g. 10,11,12):", input -> {
              List<Integer> slots = parseSlotList(input, maxSlot);
              if (slots == null) {
                PlayerUtils.sendMessage(player, "§cInvalid slots input. Must be numbers between 0 and " + maxSlot, lang.getPrefix(), TypeMessage.CHAT);
              } else {
                rotationShop.setRotationSlots(slots);
                ctx.replaceShop(modId, rotationShop);
                ConfigLoader.saveShop(rotationShop);
              }
              ctx.runOnServer(() -> openShopSettings(player, rotationShop, config, modId));
            });
          }
        }));
    }

    // Shop Type Button (Slot 17)
    template.set(17, button(new ItemStack(Items.COMPASS), "§e⚙ Shop Type: §f" + shop.getType(),
      List.of(SEP,
        "§7Current: §f" + shop.getType(),
        "",
        "§fNORMAL   §7— static catalog of products.",
        "§fROTATION §7— dynamic/rotated catalog.",
        "§fCATEGORY §7— menu with sub-shops.",
        SEP,
        "§a▶ Click §7→ Cycle Type (NORMAL -> ROTATION -> CATEGORY)"),
      a -> {
        Shop newShop = null;
        if (shop instanceof NormalShop n) {
          newShop = promoteToRotation(n, Scheduler.defaultScheduler(), 3);
        } else if (shop instanceof RotationShop r) {
          CategoryShop c = new CategoryShop();
          c.setId(r.getId());
          c.setFilePath(r.getFilePath());
          c.setDisplayConfig(r.getDisplayConfig());
          c.setEconomyConfig(r.getEconomyConfig());
          c.setConditionsConfig(r.getConditionsConfig());
          c.setSoundConfig(r.getSoundConfig());
          c.setMaintenance(r.isMaintenance());
          c.setWebhookUrl(r.getWebhookUrl());
          c.setSubShops(new ArrayList<>());
          newShop = c;
        } else if (shop instanceof CategoryShop c) {
          NormalShop n = new NormalShop();
          n.setId(c.getId());
          n.setFilePath(c.getFilePath());
          n.setDisplayConfig(c.getDisplayConfig());
          n.setEconomyConfig(c.getEconomyConfig());
          n.setConditionsConfig(c.getConditionsConfig());
          n.setSoundConfig(c.getSoundConfig());
          n.setMaintenance(c.isMaintenance());
          n.setWebhookUrl(c.getWebhookUrl());
          n.setProducts(new ArrayList<>());
          newShop = n;
        }
        if (newShop != null) {
          ctx.replaceShop(modId, newShop);
          ConfigLoader.saveShop(newShop);
          openShopSettings(player, newShop, config, modId);
        }
      }));

    template.set(27, backBtn(lang, a -> ShopListEditor.openShopList(player, config, modId)));

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

    template.set(31, backBtn(lang, a -> ShopListEditor.openShopList(player, config, modId)));

    GooeyPage page = GooeyPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleShopSettings().replace("%shop%", shop.getId())))
      .build();
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

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

    template.set(49, backBtn(lang, a -> openShopSettings(player, shop, config, modId)));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleConditionList().replace("%shop%", shop.getId())));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

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
    template.set(49, backBtn(lang, a -> openConditionsList(player, shop, config, modId)));
    template.set(53, nextBtn(lang));
    new Rectangle(0, 0, 5, 9).apply(template);

    LinkedPage.Builder lp = LinkedPage.builder().template(template)
      .title(AdventureTranslator.toNative(lang.getEditorTitleAddCondition()));
    GooeyPage page = buttons.isEmpty() ? lp.build()
      : PaginationHelper.createPagesFromPlaceholders(template, buttons, lp);
    ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Class<? extends Condition>> getConditionTypes() {
    try {
      var field = ConditionAdapter.class.getDeclaredField("TYPES");
      field.setAccessible(true);
      return (Map<String, Class<? extends Condition>>) field.get(null);
    } catch (Exception e) {
      return null;
    }
  }

  private static RotationShop promoteToRotation(NormalShop source, Scheduler scheduler, int amount) {
    RotationShop rotation = new RotationShop();
    rotation.setId(source.getId());
    rotation.setFilePath(source.getFilePath());
    rotation.setDisplayConfig(source.getDisplayConfig());
    rotation.setEconomyConfig(source.getEconomyConfig());
    rotation.setConditionsConfig(source.getConditionsConfig());
    rotation.setSoundConfig(source.getSoundConfig());
    rotation.setMaintenance(source.isMaintenance());
    rotation.setWebhookUrl(source.getWebhookUrl());
    rotation.setProducts(source.getProducts());
    rotation.setScheduler(scheduler);
    rotation.setRotationAmount(Math.max(1, amount));
    return rotation;
  }
}
