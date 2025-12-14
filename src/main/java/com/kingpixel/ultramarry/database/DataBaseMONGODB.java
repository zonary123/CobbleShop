package com.kingpixel.ultramarry.database;

import com.kingpixel.ultramarry.UltraMarry;
import com.kingpixel.ultramarry.models.UserInfo;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:10
 */
public class DataBaseMONGODB extends DataBaseClient {
  private static final String KEY_PLAYER_UUID = "uuid";
  private MongoClient client;
  private MongoCollection<Document> marriagesCollection;

  @Override public void connect() {
    var config = getConfig();
    var settings = MongoClientSettings.builder()
      .applicationName("CobbleMarry")
      .applyConnectionString(new ConnectionString(config.getUrl()))
      .build();
    client = MongoClients.create(settings);
    var database = client.getDatabase(config.getDatabase());
    marriagesCollection = database.getCollection("marriages");
  }

  @Override public void disconnect() {
    if (client != null) {
      client.close();
    }
    client = null;
  }

  @Override public UserInfo getUserInfo(UUID playerUUID) {
    UserInfo userInfo = CACHE.getIfPresent(playerUUID);
    if (userInfo != null) return userInfo;
    var filter = Filters.eq(KEY_PLAYER_UUID, playerUUID.toString());
    var document = marriagesCollection.find(filter).first();
    if (document == null) {
      userInfo = new UserInfo(playerUUID);
      updateUserInfo(userInfo);
    } else {
      userInfo = UserInfo.fromDocument(document);
    }
    UserInfo finalUserInfo = userInfo;
    return CACHE.get(playerUUID, k -> finalUserInfo);
  }

  @Override public void updateUserInfo(UserInfo userInfo) {
    var filter = Filters.eq(KEY_PLAYER_UUID, userInfo.getUuid().toString());
    var document = userInfo.toDocument();
    marriagesCollection.replaceOne(filter, document, new com.mongodb.client.model.ReplaceOptions().upsert(true));
  }

  @Override public void divorce(UUID playerUUID) {
    var filter = Filters.eq(KEY_PLAYER_UUID, playerUUID.toString());
    var document = marriagesCollection.find(filter).first();
    if (document == null) return;
    var userInfo = UserInfo.fromDocument(document);
    var userMarriedToUUID = userInfo.getMarriedTo();
    // Player Divorce
    helpDivorce(userInfo);
    // Married Player Divorce
    if (userMarriedToUUID != null) {
      var marriedToInfo = getUserInfo(userMarriedToUUID);
      helpDivorce(marriedToInfo);
    }
  }

  @Override public void marry(UUID player1UUID, UUID player2UUID) {
    var player1Info = getUserInfo(player1UUID);
    var player2Info = getUserInfo(player2UUID);
    var player1 = UltraMarry.server.getPlayerManager().getPlayer(player1UUID);
    var player2 = UltraMarry.server.getPlayerManager().getPlayer(player2UUID);
    if (player1 == null || player2 == null) return;
    player1Info.setMarriedTo(player2);
    player2Info.setMarriedTo(player1);
    updateUserInfo(player1Info);
    updateUserInfo(player2Info);
    CACHE.invalidate(player1UUID);
    CACHE.invalidate(player2UUID);
  }

  private void helpDivorce(UserInfo userInfo) {
    userInfo.divorce();
    updateUserInfo(userInfo);
    CACHE.invalidate(userInfo.getUuid());
  }

  @Override public boolean isMarried(UUID uuid) {
    var filter = Filters.eq(KEY_PLAYER_UUID, uuid.toString());
    var document = marriagesCollection.find(filter).first();
    if (document == null) return false;
    var userInfo = UserInfo.fromDocument(document);
    return userInfo.isMarried();
  }


}
