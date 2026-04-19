package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonTransactionRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonUserRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoTransactionRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoUserRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;

/**
 * Factory that creates the appropriate repository implementations based on config.
 */
@Getter
public class RepositoryFactory {

  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private MongoClient mongoClient;

  public RepositoryFactory(DataBaseConfig config) {
    switch (config.getType()) {
      case MONGODB -> {
        String url = config.getUrl();
        if (url == null || url.isEmpty()) {
          url = "mongodb://localhost:27017";
        }
        UserRepository userRepo;
        TransactionRepository txRepo;
        try {
          mongoClient = MongoClients.create(url);
          MongoDatabase database = mongoClient.getDatabase(
            config.getDatabase() != null ? config.getDatabase() : "ultrashop"
          );
          userRepo = new MongoUserRepository(database);
          txRepo = new MongoTransactionRepository(database);
          UltraShop.LOGGER.info("Connected to MongoDB: {}", url);
        } catch (Exception e) {
          UltraShop.LOGGER.error("Failed to connect to MongoDB: {}. Falling back to JSON.", e.getMessage());
          mongoClient = null;
          userRepo = new JsonUserRepository();
          txRepo = new JsonTransactionRepository();
        }
        this.userRepository = userRepo;
        this.transactionRepository = txRepo;
      }
      default -> {
        // JSON fallback for all other types until implemented
        this.userRepository = new JsonUserRepository();
        this.transactionRepository = new JsonTransactionRepository();
      }
    }
  }

  /**
   * Closes external connections (e.g. MongoDB client).
   */
  public void close() {
    if (mongoClient != null) {
      mongoClient.close();
      UltraShop.LOGGER.info("MongoDB connection closed");
    }
  }
}


