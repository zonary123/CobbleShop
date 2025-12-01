package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.UserInfo;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:04
 */
public class DataBaseMySQL extends DataBaseClient {
  public DataBaseMySQL(DataBaseConfig config) {
    super();
  }

  @Override public void connect() {

  }

  @Override public void disconnect() {

  }

  @Override public UserInfo getUserInfo(ServerPlayerEntity player) {
    return null;
  }

  @Override public void updateUserInfo(UserInfo userInfo) {

  }

  @Override
  public void addTransaction(ServerPlayerEntity player, Shop shop, Product product, ActionShop action, int amount, BigDecimal value) {

  }
}
