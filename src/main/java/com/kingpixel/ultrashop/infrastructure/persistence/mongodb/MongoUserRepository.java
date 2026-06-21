package com.kingpixel.ultrashop.infrastructure.persistence.mongodb;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ProductLimit;
import com.kingpixel.ultrashop.domain.model.UserInfo;
import com.kingpixel.ultrashop.infrastructure.persistence.UserRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MongoDB-backed user repository. Uses an in-memory cache with write-through to Mongo.
 */
public class MongoUserRepository implements UserRepository {

  private final MongoCollection<Document> collection;
  private final ConcurrentMap<UUID, UserInfo> cache = new ConcurrentHashMap<>();

  public MongoUserRepository(MongoDatabase database) {
    this.collection = database.getCollection("users");
  }

  @Override
  public UserInfo findByUuid(UUID uuid) {
    return cache.computeIfAbsent(uuid, this::loadFromMongo);
  }

  /**
   * Loads user info for a player and caches it.
   */
  public UserInfo findByPlayer(ServerPlayerEntity player) {
    return cache.computeIfAbsent(player.getUuid(), uuid -> {
      UserInfo loaded = loadFromMongo(uuid);
      if (loaded == null) {
        loaded = new UserInfo(uuid, player.getGameProfile().getName());
        saveToMongo(loaded);
      }
      return loaded;
    });
  }

  @Override
  public void save(UserInfo userInfo) {
    cache.put(userInfo.getUuid(), userInfo);
    saveToMongo(userInfo);
  }

  @Override
  public void remove(UUID uuid) {
    cache.remove(uuid);
  }

  // --- Private ---

  private UserInfo loadFromMongo(UUID uuid) {
    try {
      Document doc = collection.find(Filters.eq("_id", uuid.toString())).first();
      if (doc == null) return null;
      return documentToUserInfo(doc);
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error loading user {} from MongoDB: {}", uuid, e.getMessage());
      return null;
    }
  }

  private void saveToMongo(UserInfo userInfo) {
    try {
      Document doc = userInfoToDocument(userInfo);
      collection.replaceOne(
        Filters.eq("_id", userInfo.getUuid().toString()),
        doc,
        new ReplaceOptions().upsert(true)
      );
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error saving user {} to MongoDB: {}", userInfo.getUuid(), e.getMessage());
    }
  }

  private Document userInfoToDocument(UserInfo info) {
    Document doc = new Document("_id", info.getUuid().toString())
      .append("name", info.getName());

    Document limits = new Document();
    for (Map.Entry<UUID, ProductLimit> entry : info.getCooldownProduct().entrySet()) {
      ProductLimit pl = entry.getValue();
      limits.put(entry.getKey().toString(), new Document()
        .append("uuid", pl.getUuid().toString())
        .append("amount", pl.getAmount())
        .append("cooldown", pl.getCooldown()));
    }
    doc.append("cooldownProduct", limits);

    Document sellEarnings = new Document();
    if (info.getDailySellEarnings() != null) {
      for (Map.Entry<String, BigDecimal> entry : info.getDailySellEarnings().entrySet()) {
        if (entry.getValue() != null) {
          sellEarnings.put(entry.getKey(), entry.getValue().toPlainString());
        }
      }
    }
    doc.append("dailySellEarnings", sellEarnings);
    doc.append("dailySellReset", info.getDailySellReset());

    Document limitsSell = new Document();
    if (info.getCooldownProductSell() != null) {
      for (Map.Entry<UUID, ProductLimit> entry : info.getCooldownProductSell().entrySet()) {
        ProductLimit pl = entry.getValue();
        if (pl != null) {
          limitsSell.put(entry.getKey().toString(), new Document()
            .append("uuid", pl.getUuid().toString())
            .append("amount", pl.getAmount())
            .append("cooldown", pl.getCooldown()));
        }
      }
    }
    doc.append("cooldownProductSell", limitsSell);

    Document shopSellEarnings = new Document();
    if (info.getShopDailySellEarnings() != null) {
      for (Map.Entry<String, Map<String, BigDecimal>> shopEntry : info.getShopDailySellEarnings().entrySet()) {
        Document curEarnings = new Document();
        if (shopEntry.getValue() != null) {
          for (Map.Entry<String, BigDecimal> entry : shopEntry.getValue().entrySet()) {
            if (entry.getValue() != null) {
              curEarnings.put(entry.getKey(), entry.getValue().toPlainString());
            }
          }
        }
        shopSellEarnings.put(shopEntry.getKey(), curEarnings);
      }
    }
    doc.append("shopDailySellEarnings", shopSellEarnings);

    Document shopSellReset = new Document();
    if (info.getShopDailySellReset() != null) {
      for (Map.Entry<String, Long> entry : info.getShopDailySellReset().entrySet()) {
        if (entry.getValue() != null) {
          shopSellReset.put(entry.getKey(), entry.getValue());
        }
      }
    }
    doc.append("shopDailySellReset", shopSellReset);

    return doc;
  }

  private UserInfo documentToUserInfo(Document doc) {
    UserInfo info = new UserInfo();
    info.setUuid(UUID.fromString(doc.getString("_id")));
    info.setName(doc.getString("name"));

    Document limits = doc.get("cooldownProduct", Document.class);
    if (limits != null) {
      Map<UUID, ProductLimit> map = new HashMap<>();
      for (String key : limits.keySet()) {
        Document plDoc = limits.get(key, Document.class);
        if (plDoc != null) {
          ProductLimit pl = new ProductLimit();
          pl.setUuid(UUID.fromString(plDoc.getString("uuid")));
          pl.setAmount(plDoc.getInteger("amount", 0));
          pl.setCooldown(plDoc.getLong("cooldown"));
          map.put(UUID.fromString(key), pl);
        }
      }
      info.setCooldownProduct(map);
    }

    if (doc.containsKey("dailySellReset")) {
      Long resetVal = doc.getLong("dailySellReset");
      info.setDailySellReset(resetVal != null ? resetVal : 0L);
    } else {
      info.setDailySellReset(0L);
    }

    Document sellEarnings = doc.get("dailySellEarnings", Document.class);
    if (sellEarnings != null) {
      Map<String, BigDecimal> map = new HashMap<>();
      for (String key : sellEarnings.keySet()) {
        String valStr = sellEarnings.getString(key);
        if (valStr != null) {
          try {
            map.put(key, new BigDecimal(valStr));
          } catch (Exception ignored) {
          }
        }
      }
      info.setDailySellEarnings(map);
    }

    Document limitsSell = doc.get("cooldownProductSell", Document.class);
    if (limitsSell != null) {
      Map<UUID, ProductLimit> map = new HashMap<>();
      for (String key : limitsSell.keySet()) {
        Document plDoc = limitsSell.get(key, Document.class);
        if (plDoc != null) {
          ProductLimit pl = new ProductLimit();
          pl.setUuid(UUID.fromString(plDoc.getString("uuid")));
          pl.setAmount(plDoc.getInteger("amount", 0));
          pl.setCooldown(plDoc.getLong("cooldown"));
          map.put(UUID.fromString(key), pl);
        }
      }
      info.setCooldownProductSell(map);
    }

    Document shopSellEarnings = doc.get("shopDailySellEarnings", Document.class);
    if (shopSellEarnings != null) {
      Map<String, Map<String, BigDecimal>> map = new HashMap<>();
      for (String shopKey : shopSellEarnings.keySet()) {
        Document curEarnings = shopSellEarnings.get(shopKey, Document.class);
        if (curEarnings != null) {
          Map<String, BigDecimal> innerMap = new HashMap<>();
          for (String key : curEarnings.keySet()) {
            String valStr = curEarnings.getString(key);
            if (valStr != null) {
              try {
                innerMap.put(key, new BigDecimal(valStr));
              } catch (Exception ignored) {
              }
            }
          }
          map.put(shopKey, innerMap);
        }
      }
      info.setShopDailySellEarnings(map);
    }

    Document shopSellReset = doc.get("shopDailySellReset", Document.class);
    if (shopSellReset != null) {
      Map<String, Long> map = new HashMap<>();
      for (String key : shopSellReset.keySet()) {
        Long val = shopSellReset.getLong(key);
        if (val != null) {
          map.put(key, val);
        }
      }
      info.setShopDailySellReset(map);
    }

    return info;
  }
}

