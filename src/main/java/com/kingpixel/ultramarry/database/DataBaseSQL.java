package com.kingpixel.ultramarry.database;

import com.kingpixel.ultramarry.models.UserInfo;

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

  @Override public void updateUserInfo(UserInfo userInfo) {

  }

  @Override public void divorce(UUID playerUUID) {

  }

  @Override public void marry(UUID player1UUID, UUID player2UUID) {

  }

  @Override public boolean isMarried(UUID uuid) {
    return false;
  }

}
