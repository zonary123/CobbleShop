package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.UserInfo;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:10
 */
public class DataBaseJSON extends DataBaseClient {


  public DataBaseJSON(DataBaseConfig config) {
    super();
  }

  @Override public void connect() {

  }

  @Override public void disconnect() {

  }

  @Override public UserInfo getUserInfo(ServerPlayerEntity player) {
    UserInfo userInfo = DataBaseFactory.users.get(player.getUuid());
    if (userInfo == null) {
      userInfo = new UserInfo(player);
      userInfo.read(player);
    }
    DataBaseFactory.users.put(player.getUuid(), userInfo);
    return userInfo;
  }

  @Override public void updateUserInfo(UserInfo userInfo) {

  }

  @Override
  public void addTransaction(ServerPlayerEntity player, Shop shop, Product product, ActionShop action, int amount, BigDecimal value) {

  }
}
