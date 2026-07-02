package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.SubShop;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu shop. Exposes {@link #subShops} that link to other shops as categories.
 * Holds no products of its own.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public final class CategoryShop extends AbstractShop implements Shop {

  private List<SubShop> subShops;

  public CategoryShop() {
    super();
    this.subShops = new ArrayList<>();
  }

  @Override
  public ShopType getType() {
    return ShopType.CATEGORY;
  }

  /**
   * Categories never expose products directly — clients must navigate sub-shops.
   */
  @Override
  public List<Product> activeProducts() {
    return List.of();
  }

  @Override
  public <R> R accept(ShopVisitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void check() {
    checkConfigs();
    if (displayConfig != null && displayConfig.isAutoPlace()) {
      displayConfig = displayConfig.toBuilder().autoPlace(true).build();
    }
    if (subShops == null) subShops = new ArrayList<>();
  }
}

