package com.kingpixel.ultrashop.database;

import com.kingpixel.ultrashop.models.ActionShop;
import com.kingpixel.ultrashop.models.Product;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.UserInfo;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:04
 */
public class DataBaseSQLite extends DataBaseClient {
  public DataBaseSQLite(DataBaseConfig config) {
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
