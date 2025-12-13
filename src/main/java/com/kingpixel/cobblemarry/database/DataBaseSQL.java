package com.kingpixel.cobblemarry.database;

import com.kingpixel.cobblemarry.models.UserInfo;

import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:10
 */
public class DataBaseSQL extends DataBaseClient {


  @Override public void connect() {

  }

  @Override public void disconnect() {

  }

  @Override public UserInfo getUserInfo(UUID playerUUID) {
    return null;
  }

  @Override public void updateUserInfo(UUID playerUUID, UserInfo userInfo) {

  }

  @Override public void divorce(UUID playerUUID) {

  }

  @Override public boolean isMarried(UUID uuid) {
    return false;
  }

}
