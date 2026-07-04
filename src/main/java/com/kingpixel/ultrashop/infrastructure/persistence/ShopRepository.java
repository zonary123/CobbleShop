package com.kingpixel.ultrashop.infrastructure.persistence;

import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import java.util.List;

/**
 * Repository interface for managing shop configuration templates.
 */
public interface ShopRepository {
  List<Shop> loadAllShops(ShopOptionsApi options);
  void save(Shop shop);
  void delete(Shop shop);
}
