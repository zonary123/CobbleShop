package com.kingpixel.ultrashop.models;

import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.adapters.ShopType;
import com.kingpixel.ultrashop.adapters.ShopTypeDynamic;
import com.kingpixel.ultrashop.adapters.ShopTypeDynamicWeekly;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import lombok.Data;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:11
 */
@Data
public class DataShop {
  // ModId -> ShopId -> DynamicProduct
  public ConcurrentMap<String, Map<String, DynamicProduct>> products;

  public DataShop() {
    products = new ConcurrentHashMap<>();
  }

  public void init() {
    File file = Utils.getAbsolutePath(UltraShop.PATH_DATA);
    if (!file.exists()) {
      file.mkdirs();
    }

    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(UltraShop.PATH_DATA, "dataShop.json", call -> {
      try {
        UltraShop.dataShop = UltraShop.gsonWithOutSpaces.fromJson(call, DataShop.class);
        if (UltraShop.dataShop == null) UltraShop.dataShop = new DataShop();
        check();
        write();
      } catch (Exception e) {
        e.printStackTrace();
        UltraShop.dataShop = new DataShop();
        write();
      }
    });

    if (Boolean.FALSE.equals(futureRead.join())) {
      UltraShop.dataShop = new DataShop();
      write();
    }
  }


  public synchronized void write() {
    File file = Utils.getAbsolutePath(UltraShop.PATH_DATA + "dataShop.json");
    if (UltraShop.dataShop == null) return;
    Utils.writeFileSync(file, UltraShop.gsonWithOutSpaces.toJson(UltraShop.dataShop));
  }

  public void check() {
    boolean changed = false;
    // Todo: Check if the shop exists


    // Todo: Check if the product exists
    if (changed) {
      write();
    }
  }

  public List<Product> updateDynamicProducts(Shop shop, ShopOptionsApi options) {
    products.computeIfAbsent(options.getModId(), k -> new HashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicProduct());

    DynamicProduct dynamicProduct = products.get(options.getModId()).get(shop.getId());

    if (dynamicProduct.getTimeToUpdate() < System.currentTimeMillis()
      || dynamicProduct.getProducts().isEmpty()
      || dynamicProduct.getProducts().size() != getRotationProducts(shop)) {
      CompletableFuture.runAsync(() -> {
          dynamicProduct.setTimeToUpdate(System.currentTimeMillis() + getCooldown(shop.getType()));
          List<Product> nuevosProductos = getNewProducts(shop, options);
          dynamicProduct.setProducts(nuevosProductos);
          write();
          UltraShop.initSellProduct(options);
        }, UltraShop.SHOP_EXECUTOR)
        .orTimeout(5, TimeUnit.SECONDS)
        .exceptionally(e -> {
          e.printStackTrace();
          return null;
        });
    }
    return dynamicProduct.getProducts();
  }

  private int getRotationProducts(Shop shop) {
    ShopType shopType = shop.getType();
    if (shopType instanceof ShopTypeDynamic shopTypeDynamic) {
      return shopTypeDynamic.getProductsRotation();
    } else if (shopType instanceof ShopTypeDynamicWeekly shopTypeDynamicWeekly) {
      return shopTypeDynamicWeekly.getProductsRotation();
    }
    return 3;
  }

  public long getCooldown(ShopType shopType) {
    DurationValue cooldown = null;
    if (shopType instanceof ShopTypeDynamic shopTypeDynamic) {
      cooldown = shopTypeDynamic.getCooldown();
    } else if (shopType instanceof ShopTypeDynamicWeekly shopTypeDynamicWeekly) {
      cooldown = shopTypeDynamicWeekly.getCooldown();
    }
    return cooldown == null
      ? TimeUnit.MINUTES.toMillis(30)
      : cooldown.toMillis();
  }

  public List<Product> getNewProducts(Shop shop, ShopOptionsApi options) {
    int productsRotation = getRotationProducts(shop);
    List<Product> products = new ArrayList<>(shop.getProducts());
    // Shuffle the list to get random products
    Collections.shuffle(products);
    // Obtain the number of products in rotation
    if (products.size() > productsRotation) {
      products = products.subList(0, productsRotation);
    }
    return products;
  }

  public long getActualCooldown(Shop shop, ShopOptionsApi shopOptionsApi) {
    return products
      .computeIfAbsent(shopOptionsApi.getModId(), k -> new HashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicProduct())
      .getTimeToUpdate();
  }
}
