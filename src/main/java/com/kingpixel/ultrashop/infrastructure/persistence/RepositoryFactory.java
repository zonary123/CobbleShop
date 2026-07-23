package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.mongodb.MongoDBManager;
import com.kingpixel.cobbleutils.util.mongodb.MongoDBService;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonTransactionRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonUserRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonStockRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonShopRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoTransactionRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoUserRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoStockRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoShopRepository;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;

/**
 * Factory that creates the appropriate repository implementations based on config.
 *
 * <p>Connection lifecycle is delegated to CobbleUtils' shared pool
 * ({@link MongoDBService}). UltraShop borrows a {@link MongoDBManager} reference
 * but NEVER closes it — the pool is shared across all mods that use the same
 * {@link DataBaseConfig} fingerprint (host + database + credentials).</p>
 */
@Getter
public class RepositoryFactory {

  private static final String DEFAULT_DATABASE = "ultrashop";

  private final UserRepository userRepository;
  private final TransactionRepository transactionRepository;
  private final StockRepository stockRepository;
  private final ShopRepository shopRepository;

  public RepositoryFactory(DataBaseConfig config) {
    switch (config.getType()) {
      case MONGODB -> {
        UserRepository userRepo;
        TransactionRepository txRepo;
        StockRepository stockRepo;
        ShopRepository sRepo;
        try {
          MongoDBManager manager = MongoDBService.getOrCreateManager(config);
          String dbName = (config.getDatabase() != null && !config.getDatabase().isBlank())
            ? config.getDatabase()
            : DEFAULT_DATABASE;
          MongoDatabase database = manager.getDatabase(dbName);

          userRepo = new MongoUserRepository(database);
          txRepo = new MongoTransactionRepository(database);
          stockRepo = new MongoStockRepository(database);
          sRepo = new MongoShopRepository(database);
          UltraShop.LOGGER.info("Connected to MongoDB '{}' via CobbleUtils shared pool (active pools: {})",
            dbName, MongoDBService.getActiveConnections());
        } catch (Exception e) {
          UltraShop.LOGGER.error("Failed to acquire MongoDB manager. Falling back to JSON.", e);
          userRepo = new JsonUserRepository();
          txRepo = new JsonTransactionRepository();
          stockRepo = new JsonStockRepository();
          sRepo = new JsonShopRepository();
        }
        this.userRepository = userRepo;
        this.transactionRepository = txRepo;
        this.stockRepository = stockRepo;
        this.shopRepository = sRepo;
      }
      default -> {
        // JSON fallback for all other types
        this.userRepository = new JsonUserRepository();
        this.transactionRepository = new JsonTransactionRepository();
        this.stockRepository = new JsonStockRepository();
        this.shopRepository = new JsonShopRepository();
      }
    }
  }

  /**
   * No-op: connections are owned by CobbleUtils' shared pool.
   * UltraShop must NOT close the underlying {@link com.mongodb.client.MongoClient}
   * because other mods may still be using it.
   */
  public void close() {
    // intentionally empty — see class-level Javadoc.
  }
}
