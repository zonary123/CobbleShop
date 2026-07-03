package com.kingpixel.ultrashop.presentation.gui.edit;

import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.RotationScope;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class EditorHelpers {

  static final String SEP = "§8─────────────────────";

  private EditorHelpers() {
  }

  static GooeyButton button(ItemStack icon, String name, List<String> lore, Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(icon)
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .onClick(onClick)
      .build();
  }

  static GooeyButton button(Item item, String name, List<String> lore, Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .onClick(onClick)
      .build();
  }

  static GooeyButton button(Item item, String name, List<String> lore) {
    return GooeyButton.builder()
      .display(new ItemStack(item))
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(name))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .build();
  }

  static GooeyButton priceBtn(String label, Supplier<BigDecimal> getter,
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
        ProductEditor.openProductEditor(player, shop, product, config, modId);
      })
      .build();
  }

  static GooeyButton closeBtn(LangConfig lang, ServerPlayerEntity player) {
    return GooeyButton.builder()
      .display(lang.getGlobalItemClose().getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(lang.getEditorButtonClose()))
      .onClick(a -> ca.landonjw.gooeylibs2.api.UIManager.closeUI(player))
      .build();
  }

  static GooeyButton backBtn(LangConfig lang, Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(lang.getGlobalItemPrevious().getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(lang.getEditorButtonBack()))
      .onClick(onClick::accept)
      .build();
  }

  static void sendConfiguredMessage(ServerPlayerEntity player, String message) {
    LangConfig lang = ShopContext.get().getLang();
    PlayerUtils.sendMessage(player,
      message.replace("%prefix%", lang.getPrefix()),
      lang.getPrefix(), TypeMessage.CHAT);
  }

  static LinkedPageButton prevBtn(LangConfig lang) {
    return LinkedPageButton.builder()
      .display(lang.getGlobalItemPrevious().getItemStack()).linkType(LinkType.Previous).build();
  }

  static LinkedPageButton nextBtn(LangConfig lang) {
    return LinkedPageButton.builder()
      .display(lang.getGlobalItemNext().getItemStack()).linkType(LinkType.Next).build();
  }

  static String fmt(BigDecimal value) {
    return value != null ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0.00";
  }

  static String orEmpty(String value) {
    return value != null && !value.isEmpty() ? value : "§8none";
  }

  static String boolIcon(boolean value) {
    return value ? "§a✓ Yes" : "§c✗ No";
  }

  static String truncate(String s, int max) {
    if (s == null) return "§8null";
    return s.length() > max ? s.substring(0, max) + "§8..." : s;
  }

  static String productTypeTag(Product product) {
    String id = product.getProduct();
    if (id == null) return "§8[?]";
    if (id.startsWith("command:")) return "§d[CMD]";
    if (id.startsWith("pokemon:")) return "§b[PKM]";
    if (id.startsWith("item:") || id.contains("#")) return "§a[ITEM+]";
    return "§f[ITEM]";
  }

  static int safeMaxStack(Product product) {
    try {
      return product.getMaxStack();
    } catch (Exception e) {
      return -1;
    }
  }

  static String itemStackToProductId(ItemStack stack) {
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

  static String getPokemonPropertiesString(Pokemon pokemon) {
    StringBuilder sb = new StringBuilder();
    sb.append(pokemon.getSpecies().showdownId());
    if (pokemon.getShiny()) {
      sb.append(" shiny=yes");
    }
    sb.append(" level=").append(pokemon.getLevel());
    if (pokemon.getForm() != null && !pokemon.getForm().getName().equalsIgnoreCase("normal")) {
      sb.append(" form=").append(pokemon.getForm().getName().toLowerCase());
    }
    if (pokemon.getGender() != null) {
      sb.append(" gender=").append(pokemon.getGender().getShowdownName().toLowerCase());
    }
    if (pokemon.getNature() != null) {
      sb.append(" nature=").append(pokemon.getNature().getName().getPath().toLowerCase());
    }
    if (pokemon.getAbility() != null) {
      sb.append(" ability=").append(pokemon.getAbility().getName().toLowerCase());
    }
    return sb.toString();
  }

  static RotationShop newRotationFrom(Shop shop, List<Product> pool) {
    RotationShop rotation = new RotationShop();
    rotation.setId(shop.getId());
    rotation.setFilePath(shop.getFilePath());
    rotation.setDisplayConfig(shop.getDisplayConfig());
    rotation.setEconomyConfig(shop.getEconomyConfig());
    rotation.setConditionsConfig(shop.getConditionsConfig());
    rotation.setSoundConfig(shop.getSoundConfig());
    rotation.setMaintenance(shop.isMaintenance());
    rotation.setWebhookUrl(shop.getWebhookUrl());
    rotation.setProducts(new ArrayList<>(pool));
    rotation.setRotationAmount(3);
    rotation.setScheduler(Scheduler.defaultScheduler());
    return rotation;
  }

  static List<Integer> parseSlotList(String input, int maxSlot) {
    if (input == null || input.isBlank()) {
      return List.of();
    }
    List<Integer> slots = new ArrayList<>();
    for (String part : input.split("[,;\\s]+")) {
      if (part.isBlank()) continue;
      try {
        int slot = Integer.parseInt(part.trim());
        if (slot < 0 || slot > maxSlot) return null;
        slots.add(slot);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return slots;
  }
}
