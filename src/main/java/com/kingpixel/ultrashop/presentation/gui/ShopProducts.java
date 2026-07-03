package com.kingpixel.ultrashop.presentation.gui;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopVisitor;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

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
   */
  public static List<Product> activeProducts(Shop shop, String modId) {
    return activeProducts(shop, modId, null);
  }

  /**
   * Products visible to the player RIGHT NOW. Rotation shops require {@code player}
   * when {@code rotationScope} is {@code PLAYER}.
   */
  public static List<Product> activeProducts(Shop shop, String modId, @Nullable ServerPlayerEntity player) {
    return shop.accept(new ShopVisitor<List<Product>>() {
      @Override public List<Product> visit(NormalShop s) {
        return s.getProducts();
      }
      @Override public List<Product> visit(CategoryShop s) {
        return List.of();
      }
      @Override public List<Product> visit(RotationShop s) {
        return ShopContext.get().getDataShop().updateDynamicProducts(s, modId, player, false);
      }
    });
  }

  /**
   * EVERY product configured on the shop, regardless of rotation state.
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
        return s.getProducts();
      }
    });
  }

  public static boolean isRotation(Shop shop) {
    return shop instanceof RotationShop;
  }

  public static boolean isCategory(Shop shop) {
    return shop instanceof CategoryShop;
  }
}
