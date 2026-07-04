package com.kingpixel.ultrashop.infrastructure.persistence.json;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.infrastructure.persistence.ShopRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local JSON file implementation of ShopRepository.
 */
public class JsonShopRepository implements ShopRepository {

  @Override
  public List<Shop> loadAllShops(ShopOptionsApi options) {
    List<Shop> loadedShops = new ArrayList<>();
    Path shopDir = CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");
    if (!Files.exists(shopDir)) {
      try {
        Files.createDirectories(shopDir);
      } catch (IOException e) {
        UltraShop.LOGGER.error("Failed to create shop directory: " + shopDir, e);
        return loadedShops;
      }
    }

    List<Path> jsonFiles = new ArrayList<>(UtilsFile.getAllJsonFiles(shopDir));
    jsonFiles.removeIf(file -> {
      Path relative = shopDir.relativize(file);
      for (Path part : relative) {
        String name = part.toString().toLowerCase();
        if (name.startsWith("_") || name.contains("backup")) {
          return true;
        }
      }
      return false;
    });

    for (Path file : jsonFiles) {
      try {
        Shop shopLoaded = UtilsFile.read(file, Shop.class);
        if (shopLoaded == null) continue;

        String shopId = file.getFileName().toString().replace(".json", "");
        if (shopLoaded instanceof com.kingpixel.ultrashop.domain.model.shop.AbstractShop a) {
          a.setId(shopId);
        }
        shopLoaded.setFilePath(file.toString());
        shopLoaded.check();

        // Rewrite to ensure canonical formatting
        UtilsFile.write(file, shopLoaded);

        loadedShops.add(shopLoaded);
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error loading shop " + file, e);
      }
    }
    return loadedShops;
  }

  @Override
  public void save(Shop shop) {
    if (shop.getFilePath() == null) return;
    Path path = Path.of(shop.getFilePath());
    UtilsFile.writeAsync(path, shop).exceptionally(ex -> {
      UltraShop.LOGGER.error("Error saving shop " + shop.getId(), ex);
      return null;
    });
  }

  @Override
  public void delete(Shop shop) {
    if (shop.getFilePath() == null) return;
    try {
      Files.deleteIfExists(Path.of(shop.getFilePath()));
    } catch (IOException e) {
      UltraShop.LOGGER.error("Error deleting shop " + shop.getId(), e);
    }
  }
}
