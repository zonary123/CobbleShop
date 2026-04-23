package com.kingpixel.ultrashop.presentation.gui;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopVisitor;

import java.util.List;

/**
 * Read-only helper that resolves the product list a typed {@link Shop} exposes
 * to the GUI / search layer. Centralizes the {@code instanceof}-style branching
 * behind a {@link ShopVisitor} so callers don't repeat it.
 */
public final class ShopProducts {

  private ShopProducts() {
  }

  /**
   * Products visible to the player RIGHT NOW. For rotation shops this triggers
   * the rotation cooldown check via {@code DataShop} and may rotate the catalog.
   *
   * <ul>
   *   <li>{@link NormalShop} → declared products</li>
   *   <li>{@link CategoryShop} → empty (categories navigate, they don't sell)</li>
   *   <li>{@link RotationShop} → current rotation contents (rotated if due)</li>
   * </ul>
   */
  public static List<Product> activeProducts(Shop shop, String modId) {
    return shop.accept(new ShopVisitor<List<Product>>() {
      @Override public List<Product> visit(NormalShop s) {
        return s.getProducts();
      }
      @Override public List<Product> visit(CategoryShop s) {
        return List.of();
      }
      @Override public List<Product> visit(RotationShop s) {
        return ShopContext.get().getDataShop().updateDynamicProducts(s, modId, false);
      }
    });
  }

  /**
   * EVERY product configured on the shop, regardless of rotation state.
   * Used by search / suggestion code that wants to look at the full pool.
   *
   * <ul>
   *   <li>{@link NormalShop} → declared products</li>
   *   <li>{@link CategoryShop} → empty</li>
   *   <li>{@link RotationShop} → full {@code productPool} (not just the current rotation)</li>
   * </ul>
   */
  public static List<Product> allConfiguredProducts(Shop shop) {
    return shop.accept(new ShopVisitor<List<Product>>() {
      @Override public List<Product> visit(NormalShop s) {
        return s.getProducts();
      }
      @Override public List<Product> visit(CategoryShop s) {
        return List.of();
      }
      @Override public List<Product> visit(RotationShop s) {
        return s.getProductPool();
      }
    });
  }

  /** True if this shop pages through a rotating catalog. */
  public static boolean isRotation(Shop shop) {
    return shop instanceof RotationShop;
  }

  /** True if this shop is a category menu (no products of its own). */
  public static boolean isCategory(Shop shop) {
    return shop instanceof CategoryShop;
  }
}

