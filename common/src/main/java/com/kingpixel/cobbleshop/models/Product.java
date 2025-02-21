package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.api.EconomyApi;
import lombok.Builder;
import lombok.Data;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
@Builder
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
    }
  }

  public void check() {
    // Limit Product
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

  public GooeyButton getIcon(Shop shop, ActionShop actionShop, int amount) {
    String display = this.display != null ? this.display : product;
    String title = this.displayname != null ? this.displayname : product;
    List<String> lore = new ArrayList<>(CobbleShop.lang.getInfoProduct());
    if (actionShop != null) {
      lore.removeIf(s -> {
        if (actionShop == ActionShop.BUY) {
          return s.contains("%removesell%") || s.contains("%sell%");
        } else {
          return s.contains("%removebuy%") || s.contains("%buy%");
        }
      });
    }
    lore.replaceAll(s -> replace(s, shop, amount));
    lore.replaceAll(s -> {
      if (s.contains("%info%")) {
        lore.addAll(getLore());
      }
      return s;
    });
    lore.removeIf(s -> s.contains("%info%"));
    return new ItemModel().getButton(0,
      title,
      lore,
      action -> {
        if (actionShop != null) {
          CobbleShop.lang.getMenuBuyAndSell().open(action.getPlayer(), this, amount, actionShop);
        }
      });
  }

  private String replace(String s, Shop shop, int amount) {
    String currency = shop.getCurrency();
    BigDecimal totalBuy = buy.multiply(BigDecimal.valueOf(amount));
    if (discount != null) {
      totalBuy = totalBuy.subtract(totalBuy.multiply(BigDecimal.valueOf(discount / 100)));
    }
    BigDecimal totalSell = sell.multiply(BigDecimal.valueOf(amount));
    return s
      .replace("%buy%", EconomyApi.formatMoney(totalBuy, currency))
      .replace("%price%", EconomyApi.formatMoney(totalSell, currency))
      .replace("%amount%", String.valueOf(amount))
      .replace("%discount%", discount != null ? discount + "%" : "");
  }


  public void sell(ServerPlayerEntity player, ItemStack itemStack) {

  }

  public void buy(ServerPlayerEntity player, int amount) {

  }


}
