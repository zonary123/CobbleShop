package com.kingpixel.ultrashop.infrastructure.persistence.json;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.UserInfo;
import com.kingpixel.ultrashop.infrastructure.persistence.UserRepository;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JSON file-based user repository. Thread-safe via ConcurrentHashMap cache.
 */
public class JsonUserRepository implements UserRepository {


  private final ConcurrentMap<UUID, UserInfo> cache = new ConcurrentHashMap<>();
  private final Path basePath;

  public JsonUserRepository() {
    this.basePath = CobbleUtils.getPath().resolve(UltraShop.MOD_ID).resolve("data").resolve("users");
  }

  @Override
  public UserInfo findByUuid(UUID uuid) {
    return cache.computeIfAbsent(uuid, this::loadFromDisk);
  }

  /**
   * Loads user info for a player and caches it.
   */
  public UserInfo findByPlayer(ServerPlayerEntity player) {
    return cache.computeIfAbsent(player.getUuid(), uuid -> {
      UserInfo loaded = loadFromDisk(uuid);
      if (loaded == null) {
        loaded = new UserInfo(uuid, player.getGameProfile().getName());
        saveToDisk(loaded);
      }
      return loaded;
    });
  }

  @Override
  public void save(UserInfo userInfo) {
    cache.put(userInfo.getUuid(), userInfo);
    saveToDisk(userInfo);
  }

  @Override
  public void remove(UUID uuid) {
    cache.remove(uuid);
  }

  // --- Private ---

  private UserInfo loadFromDisk(UUID uuid) {
    Path filePath = basePath.resolve(uuid.toString() + ".json");
    try {
      if (UtilsFile.exists(filePath)) {
        return UtilsFile.read(filePath, UserInfo.class);
      }
    } catch (Exception e) {
      UltraShop.LOGGER.error( "Error reading user " + uuid + ": " + e.getMessage());
    }
    return null;
  }

  private void saveToDisk(UserInfo userInfo) {
    Path filePath = basePath.resolve(userInfo.getUuid().toString() + ".json");
    UtilsFile.writeAsync(filePath, userInfo)
      .exceptionally(e -> {
        UltraShop.LOGGER.error( "Error writing user " + userInfo.getUuid() + ": " + e.getMessage());
        return null;
      });
  }
}
