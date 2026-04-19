package com.kingpixel.ultrashop.infrastructure.persistence.mongodb;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.infrastructure.persistence.TransactionRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB-backed transaction repository.
 * Stores each transaction as a document with indexed fields for efficient querying.
 */
public class MongoTransactionRepository implements TransactionRepository {

  private final MongoCollection<Document> collection;

  public MongoTransactionRepository(MongoDatabase database) {
    this.collection = database.getCollection("transactions");
    // Create indexes for common queries
    collection.createIndex(Indexes.descending("timestamp"));
    collection.createIndex(Indexes.ascending("playerUuid"));
    collection.createIndex(Indexes.compoundIndex(
      Indexes.ascending("playerUuid"),
      Indexes.descending("timestamp")
    ));
  }

  @Override
  public void save(Transaction transaction) {
    try {
      Document doc = transactionToDocument(transaction);
      collection.insertOne(doc);
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error saving transaction to MongoDB: {}", e.getMessage());
    }
  }

  @Override
  public List<Transaction> findByPlayer(UUID playerUuid, int limit) {
    List<Transaction> result = new ArrayList<>();
    try {
      collection.find(Filters.eq("playerUuid", playerUuid.toString()))
        .sort(Sorts.descending("timestamp"))
        .limit(limit)
        .forEach(doc -> result.add(documentToTransaction(doc)));
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error querying transactions for player {}: {}", playerUuid, e.getMessage());
    }
    return result;
  }

  @Override
  public List<Transaction> findAll(int maxDays) {
    List<Transaction> result = new ArrayList<>();
    long cutoff = System.currentTimeMillis() - (long) maxDays * 86_400_000L;
    try {
      collection.find(Filters.gte("timestamp", cutoff))
        .sort(Sorts.descending("timestamp"))
        .forEach(doc -> result.add(documentToTransaction(doc)));
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error querying all transactions from MongoDB: {}", e.getMessage());
    }
    return result;
  }

  // --- Private ---

  private Document transactionToDocument(Transaction tx) {
    return new Document()
      .append("playerUuid", tx.getPlayerUuid().toString())
      .append("playerName", tx.getPlayerName())
      .append("shopId", tx.getShopId())
      .append("productId", tx.getProductId())
      .append("action", tx.getAction().name())
      .append("amount", tx.getAmount())
      .append("value", tx.getValue().toPlainString())
      .append("currency", tx.getCurrency())
      .append("timestamp", tx.getTimestamp());
  }

  private Transaction documentToTransaction(Document doc) {
    return Transaction.builder()
      .playerUuid(UUID.fromString(doc.getString("playerUuid")))
      .playerName(doc.getString("playerName"))
      .shopId(doc.getString("shopId"))
      .productId(doc.getString("productId"))
      .action(ActionShop.valueOf(doc.getString("action")))
      .amount(doc.getInteger("amount", 0))
      .value(new BigDecimal(doc.getString("value")))
      .currency(doc.getString("currency"))
      .timestamp(doc.getLong("timestamp"))
      .build();
  }
}

