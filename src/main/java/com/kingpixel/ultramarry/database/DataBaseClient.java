package com.kingpixel.ultramarry.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultramarry.UltraMarry;
import com.kingpixel.ultramarry.models.UserInfo;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public abstract class DataBaseClient {
  public static final Cache<UUID, UserInfo> CACHE = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build();

  public DataBaseConfig getConfig() {
    return UltraMarry.config.getDatabase();
  }

  public abstract void connect();

  public abstract void disconnect();

  // GET/UPDATE/DELETE METHODS
  public abstract UserInfo getUserInfo(UUID playerUUID);

  public abstract void updateUserInfo(UserInfo userInfo);

  public abstract void divorce(UUID playerUUID);

  public abstract void marry(UUID player1UUID, UUID player2UUID);

  // CHECK METHODS
  public boolean isMarried(ServerPlayerEntity player) {
    return isMarried(player.getUuid());
  }

  public abstract boolean isMarried(UUID uuid);

}
