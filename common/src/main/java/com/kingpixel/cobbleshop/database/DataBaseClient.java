package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleshop.models.*;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public abstract class DataBaseClient {

  public abstract void connect();

  public abstract void disconnect();

  public abstract UserInfo getUserInfo(ServerPlayerEntity player);

  public abstract void updateUserInfo(UserInfo userInfo);

  public abstract void addTransaction(ServerPlayerEntity player, Shop shop, Product product, ActionShop action, int amount,
                                      BigDecimal value);

  public void addProductLimit(ServerPlayerEntity player, Shop shop, Product product, int amount) {
    UserInfo userInfo = getUserInfo(player);
    userInfo.addProductLimit(product, amount);
    userInfo.write(player);
  }

  public boolean canBuy(ServerPlayerEntity player, Product product) {
    if (product.getUuid() == null) return true;
    UserInfo userInfo = getUserInfo(player);
    var productLimit = userInfo.getCooldownProduct().get(product.getUuid());
    boolean isCooldown = productLimit != null && productLimit.getCooldown() > System.currentTimeMillis();
    boolean isLimit = userInfo.getProductLimit(product) >= product.getMax();
    if (isLimit) {
      if (isCooldown) {
        return false;
      } else {
        userInfo.getCooldownProduct().remove(product.getUuid());
        updateUserInfo(userInfo);
      }
    }
    return isLimit;
  }

  public long getProductCooldown(ServerPlayerEntity player, Product product) {
    UserInfo userInfo = getUserInfo(player);
    ProductLimit limit = userInfo.getCooldownProduct().get(product.getUuid());
    return limit == null ? System.currentTimeMillis() : limit.getCooldown();
  }
}
