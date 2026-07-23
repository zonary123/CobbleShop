package com.kingpixel.ultrashop.infrastructure.persistence.mongodb;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.StockMode;
import com.kingpixel.ultrashop.infrastructure.persistence.StockRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.UUID;

/**
 * MongoDB stock repository.
 * Uses atomic findOneAndUpdate for cross-server safe stock consumption.
 */
public class MongoStockRepository implements StockRepository {

  private final MongoCollection<Document> collection;

  public MongoStockRepository(MongoDatabase database) {
    this.collection = database.getCollection("stock");
  }

  @Override
  public long getRemaining(UUID playerUuid, UUID productUuid, StockMode mode, long maxStock) {
    try {
      String key = key(playerUuid, productUuid, mode);
      Document doc = collection.find(Filters.eq("_id", key)).first();
      long consumed = doc != null ? doc.getLong("consumed") : 0L;
      return Math.max(0L, maxStock - consumed);
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error reading stock from MongoDB", e);
      return 0L;
    }
  }

  @Override
  public boolean tryConsume(UUID playerUuid, UUID productUuid, StockMode mode, int amount, long maxStock) {
    if (amount <= 0 || maxStock <= 0) return false;
    String key = key(playerUuid, productUuid, mode);
    try {
      collection.updateOne(
        Filters.eq("_id", key),
        Updates.combine(
          Updates.setOnInsert("_id", key),
          Updates.setOnInsert("consumed", 0L)
        ),
        new com.mongodb.client.model.UpdateOptions().upsert(true)
      );

      long maxConsumedBefore = maxStock - amount;
      if (maxConsumedBefore < 0) {
        return false;
      }

      Document updated = collection.findOneAndUpdate(
        Filters.and(
          Filters.eq("_id", key),
          Filters.lte("consumed", maxConsumedBefore)
        ),
        Updates.inc("consumed", amount),
        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      );
      return updated != null;
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error consuming stock in MongoDB", e);
      return false;
    }
  }

  @Override
  public void release(UUID playerUuid, UUID productUuid, StockMode mode, int amount) {
    if (amount <= 0) return;
    String key = key(playerUuid, productUuid, mode);
    try {
      collection.updateOne(Filters.eq("_id", key), Updates.inc("consumed", -amount));
      collection.updateOne(
        Filters.and(Filters.eq("_id", key), Filters.lt("consumed", 0L)),
        Updates.set("consumed", 0L)
      );
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error releasing stock in MongoDB", e);
    }
  }

  private static String key(UUID playerUuid, UUID productUuid, StockMode mode) {
    if (mode == StockMode.GLOBAL) {
      return "g:" + productUuid;
    }
    return "p:" + playerUuid + ":" + productUuid;
  }
}

