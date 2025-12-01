package com.kingpixel.cobbleshop.adapters;

import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Stack;

/**
 * @author Carlos Varas Alonso - 27/08/2024 21:49
 */
@Getter
@Setter
@ToString
public abstract class ShopType {
  private TypeShop typeShop;

  public static ShopType get(String type) {
    return switch (TypeShop.valueOf(type)) {
      case DYNAMIC -> new ShopTypeDynamic();
      case DYNAMIC_WEEKLY -> new ShopTypeDynamicWeekly();
      case PERMANENT -> new ShopTypePermanent();
      case WEEKLY -> new ShopTypeWeekly();
      case DYNAMIC_CALENDAR -> new ShopTypeDynamicCalendar();
      case CALENDAR -> new ShopTypeCalendar();
      default -> new ShopTypePermanent();
    };
  }


  public List<Product> getProducts(Shop shop, ShopOptionsApi options) {
    return shop.getProducts();
  }

  public void check() {
    if (typeShop == null) typeShop = TypeShop.PERMANENT;
  }

  public boolean isOpen() {
    return true;
  }

  public String replace(String text, ShopOptionsApi shopOptionsApi, Shop shop) {
    return "";
  }

  public abstract String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi);

  /**
   * Buy a product from the shop
   *
   * @param player  The player who is buying the product
   * @param product The product to buy
   * @param amount  The amount of product to buy
   */
  public boolean buyProduct(ServerPlayerEntity player, Product product, Shop shop, int amount,
                            ShopOptionsApi options, Config config, Stack<Shop> stack, boolean withClose) {
    return product.buy(player, shop, amount, options, config, stack, withClose);
  }
}


