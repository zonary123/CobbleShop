package com.kingpixel.ultrashop.infrastructure.persistence.mongodb;

import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.infrastructure.persistence.ShopRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB implementation of ShopRepository.
 */
public class MongoShopRepository implements ShopRepository {

  private final MongoCollection<Document> collection;

  public MongoShopRepository(MongoDatabase database) {
    this.collection = database.getCollection("shops");
  }

  @Override
  public List<Shop> loadAllShops(ShopOptionsApi options) {
    List<Shop> loadedShops = new ArrayList<>();
    try {
      for (Document doc : collection.find()) {
        String json = doc.getString("data");
        if (json == null) continue;
        Shop shop = UtilsFile.getGson().fromJson(json, Shop.class);
        if (shop != null) {
          shop.setFilePath("mongodb:" + shop.getId());
          shop.check();
          loadedShops.add(shop);
        }
      }
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error loading shops from MongoDB", e);
    }
    return loadedShops;
  }

  @Override
  public void save(Shop shop) {
    try {
      String json = UtilsFile.getGson().toJson(shop);
      Document doc = new Document("_id", shop.getId())
        .append("type", shop.getType().name())
        .append("data", json);
      collection.replaceOne(
        Filters.eq("_id", shop.getId()),
        doc,
        new ReplaceOptions().upsert(true)
      );
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error saving shop " + shop.getId() + " to MongoDB", e);
    }
  }

  @Override
  public void delete(Shop shop) {
    try {
      collection.deleteOne(Filters.eq("_id", shop.getId()));
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error deleting shop " + shop.getId() + " from MongoDB", e);
    }
  }
}
