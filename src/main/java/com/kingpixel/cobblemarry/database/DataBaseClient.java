package com.kingpixel.cobblemarry.database;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.cobblemarry.CobbleMarry;
import com.kingpixel.cobblemarry.models.UserInfo;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public abstract class DataBaseClient {
  public static final Cache<UUID, UserInfo> CACHE = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .build();

  public DataBaseConfig getConfig() {
    return CobbleMarry.config.getDatabase();
  }

  public abstract void connect();

  public abstract void disconnect();

  // GET/UPDATE/DELETE METHODS
  @Nullable public UserInfo getUserInfoCached(UUID playerUUID) {
    return CACHE.getIfPresent(playerUUID);
  }

  public abstract UserInfo getUserInfo(UUID playerUUID);

  public abstract void updateUserInfo(UUID playerUUID, UserInfo userInfo);

  public abstract void divorce(UUID playerUUID);

  // CHECK METHODS
  public boolean isMarried(ServerPlayerEntity player) {
    return isMarried(player.getUuid());
  }

  public abstract boolean isMarried(UUID uuid);

}
