package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.api.EconomyApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import lombok.Data;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
public class Product {
  // Compra 1 por 1
  private Boolean oneByOne;
  // Numero de compras totales que se pueden hacer
  private UUID uuid;
  private Integer max;
  private Integer cooldown;
  // Optional fields for permissions
  private String canBuyPermission;
  private String notBuyPermission;
  // Optional fields for discounts
  private Integer discount;
  // Optional fields for visual representation
  private String display;
  private String displayname;
  private List<String> lore;
  private Integer CustomModelData;
  // Essential fields
  private Integer slot;
  private String product;
  private BigDecimal buy;
  private BigDecimal sell;

  public Product() {
    product = "minecraft:stone";
    buy = BigDecimal.valueOf(9999999);
    sell = BigDecimal.valueOf(1);
  }

  public Product(boolean optional) {
    super();
    if (optional) {
      oneByOne = true;
      uuid = UUID.randomUUID();
      max = 1;
      cooldown = 60;
      canBuyPermission = "cobbleshop.dirt";
      notBuyPermission = "cobbleshop.dirt";
      discount = 10;
      display = "minecraft:dirt";
      displayname = "Custom Dirt";
      lore = List.of("This is a custom dirt", "You can use it to build");
      CustomModelData = 0;
      slot = 0;
    }
  }

  public void check() {
    // Limit Product
    if (product == null) product = "minecraft:stone";


    if (cooldown != null || max != null) {
      if (uuid == null) {
        uuid = UUID.randomUUID();
      }
      if (max == null) {
        max = 1;
      }
      if (cooldown == null) {
        cooldown = 60;
      }
    }
  }

  public GooeyButton getIcon(Shop shop, ActionShop actionShop, int amount, ShopOptionsApi options, Config config) {
    String finalDisplay = this.display != null ? this.display : product;
    ItemChance itemChance = new ItemChance(finalDisplay, 0);
    String title = this.displayname != null ? this.displayname : itemChance.getTitle();
    List<String> lore = new ArrayList<>(CobbleShop.lang.getInfoProduct());

    if (actionShop != null) {
      lore.removeIf(s -> {
        if (s.isEmpty()) return false;
        if (actionShop == ActionShop.BUY) {
          return s.contains("%removesell%") || s.contains("%sell%");
        } else {
          return s.contains("%removebuy%") || s.contains("%buy%");
        }
      });
    }

    lore.replaceAll(s -> replace(s, shop, amount));
    if (this.lore != null) {
      int infoIndex = lore.indexOf("%info%");
      if (infoIndex != -1) {
        lore.remove(infoIndex);
        lore.addAll(infoIndex, this.lore);
      }
    }
    lore.removeIf(s -> s.contains("%info%"));


    ItemStack itemStack = itemChance.getItemStack();
    itemStack.setCount(amount);
    GooeyButton.Builder builder = GooeyButton.builder()
      .display(itemStack)
      .with(DataComponentTypes.CUSTOM_NAME, AdventureTranslator.toNative(title))
      .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .with(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);

    return builder
      .onClick(action -> {
        ActionShop shopAction = null;
        switch (action.getClickType()) {
          case LEFT_CLICK, SHIFT_LEFT_CLICK -> shopAction = ActionShop.BUY;
          case RIGHT_CLICK, SHIFT_RIGHT_CLICK -> shopAction = ActionShop.SELL;
        }
        CobbleShop.lang.getMenuBuyAndSell().open(action.getPlayer(), shop, this, amount, shopAction, options, config);
      })
      .build();
  }

  private int getDiscount(Shop shop) {
    if (shop.getGlobalDiscount() <= 0) {
      return discount != null ? discount : 0;
    } else {
      return shop.getGlobalDiscount();
    }
  }

  private BigDecimal getBuyPrice(int amount, Shop shop) {
    BigDecimal totalBuy = buy.multiply(BigDecimal.valueOf(amount));

    totalBuy = totalBuy.subtract(totalBuy.multiply(BigDecimal.valueOf(getDiscount(shop) / 100)));
    return totalBuy;
  }

  private BigDecimal getSellPrice(int amount) {
    return sell.multiply(BigDecimal.valueOf(amount));
  }

  private String replace(String s, Shop shop, int amount) {
    if (s == null || s.isEmpty()) return "";
    String currency = shop.getCurrency();


    if (s.contains("%buy%")) {
      s = s.replace("%buy%", EconomyApi.formatMoney(getBuyPrice(amount, shop), currency));
    }
    if (s.contains("%sell%")) {
      s = s.replace("%sell%", EconomyApi.formatMoney(getSellPrice(amount), currency));
    }
    if (s.contains("%amount%")) {
      s = s.replace("%amount%", String.valueOf(amount));
    }
    if (s.contains("%discount%")) {
      int discount = getDiscount(shop);
      s = s.replace("%discount%", discount >= 0 ? discount + "%" : "");
    }
    if (s.contains("%removebuy%")) {
      s = s.replace("%removebuy%", "");
    }
    if (s.contains("%removesell%")) {
      s = s.replace("%removesell%", "");
    }
    return s;
  }


  public boolean isSellable() {
    return sell != null && sell.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean isBuyable() {
    return buy != null && buy.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean buy(ServerPlayerEntity player, Shop shop, int amount, ShopOptionsApi options, Config config) {
    boolean result = false;
    ItemChance itemChance = new ItemChance(product, 0);
    BigDecimal totalBuy = getBuyPrice(amount, shop);
    if (EconomyApi.hasEnoughMoney(player, totalBuy, shop.getCurrency(), false)) {
      ItemChance.giveReward(player, itemChance, amount);
      result = true;
    }
    if (result) {
      PlayerUtils.sendMessage(
        player,
        CobbleShop.lang.getMessageNotEnoughMoney()
          .replace("%product%", itemChance.getTitle())
          .replace("%amount%", String.valueOf(amount))
          .replace("%price%", EconomyApi.formatMoney(totalBuy, shop.getCurrency())),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
      shop.open(player, options, config, 0, shop);
    }
    return result;
  }

  public boolean sell(ServerPlayerEntity player, Shop shop, int amount) {
    return false;
  }

  public boolean hasErrors() {
    if (buy != null && sell != null && buy.compareTo(BigDecimal.ZERO) > 0 && sell.compareTo(BigDecimal.ZERO) > 0) {
      if (buy.compareTo(sell) < 0) {
        CobbleUtils.LOGGER.error("The sell price is lower than the buy price -> " + product);
        return true;
      }
    }


    return false;
  }

  public int getStack() {
    if (product.startsWith("command:") || product.startsWith("pokemon:") || (oneByOne != null && oneByOne)) return 1;

    return new ItemChance(product, 0).getItemStack().getMaxCount();
  }
}
