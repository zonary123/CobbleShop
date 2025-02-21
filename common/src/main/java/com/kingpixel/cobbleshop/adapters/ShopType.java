package com.kingpixel.cobbleshop.adapters;

import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 27/08/2024 21:49
 */
@Getter
@Setter
@ToString
public abstract class ShopType {
  private TypeShop typeShop;


  public List<Product> getProducts(Shop shop) {
    return shop.getProducts();
  }

  public boolean isOpen() {
    return true;
  }

  public String replace(String text, ShopOptionsApi shopOptionsApi, Shop shop) {
    return "";
  }

  public abstract String replace(String text, Shop shop, ShopOptionsApi shopOptionsApi);
}


