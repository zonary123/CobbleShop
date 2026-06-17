package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.ShopType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Static-catalog shop. All declared {@link #products} are always offered.
 * No rotation, no sub-shops.
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public final class NormalShop extends AbstractShop implements Shop {

  private List<Product> products;

  public NormalShop() {
    super();
    this.products = new ArrayList<>();
  }

  @Override
  public ShopType getType() {
    return ShopType.NORMAL;
  }

  @Override
  public List<Product> activeProducts() {
    return products;
  }

  @Override
  public <R> R accept(ShopVisitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void check() {
    checkConfigs();
    if (products == null) products = new ArrayList<>();
    products.forEach(p -> p.check(legacyView()));
    deduplicateProductUuids();
  }

  /**
   * Builds a transient legacy {@link com.kingpixel.ultrashop.domain.model.Shop}
   * view so existing {@code Product.check(Shop)} keeps compiling during the
   * coexistence phase. Removed in Phase 4 cleanup.
   */
  private com.kingpixel.ultrashop.domain.model.Shop legacyView() {
    return ShopBridge.toLegacy(this);
  }

  private void deduplicateProductUuids() {
    Set<UUID> seen = new HashSet<>();
    for (Product product : products) {
      if (product.getUuid() != null && !seen.add(product.getUuid())) {
        UltraShop.LOGGER.warn("Duplicate product UUID {} in shop {}. Regenerating.",
          product.getUuid(), getId());
        product.setUuid(UUID.randomUUID());
        seen.add(product.getUuid());
      }
    }
  }
}

