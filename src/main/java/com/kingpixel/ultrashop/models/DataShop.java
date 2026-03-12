package com.kingpixel.ultrashop.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UtilsFile;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.adapters.ShopType;
import com.kingpixel.ultrashop.adapters.ShopTypeDynamic;
import com.kingpixel.ultrashop.adapters.ShopTypeDynamicWeekly;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Data
public class DataShop {

  // ModId -> ShopId -> DynamicProduct
  private ConcurrentMap<String, ConcurrentMap<String, DynamicProduct>> products = new ConcurrentHashMap<>();

  private static final Path FILE_PATH = CobbleUtils.getPath().resolve(UltraShop.MOD_ID).resolve("data").resolve("dataShop.json");

  public void init() {
    try {
      DataShop loaded = UtilsFile.read(FILE_PATH, DataShop.class);

      if (loaded != null) {
        loaded.check();
        this.products = loaded.products;
      } else {
        write();
      }

    } catch (Exception e) {
      e.printStackTrace();
      this.products = new ConcurrentHashMap<>();
      write();
    }
  }

  public void write() {
    UtilsFile.writeAsync(FILE_PATH, this)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

  public void check() {
    boolean changed = false;

    // TODO: Check if the shop exists
    // TODO: Check if the product exists

    if (changed) {
      write();
    }
  }

  public List<Product> updateDynamicProducts(Shop shop, ShopOptionsApi options, boolean force) {

    products
      .computeIfAbsent(options.getModId(), k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicProduct());

    DynamicProduct dynamicProduct = products.get(options.getModId()).get(shop.getId());

    if (dynamicProduct.getTimeToUpdate() < System.currentTimeMillis()
      || dynamicProduct.getProducts().isEmpty()
      || dynamicProduct.getProducts().size() != getRotationProducts(shop)
      || force) {

      CompletableFuture.runAsync(() -> {

          if (shop.isAnnounceRotation()) {
            PlayerUtils.sendMessage(
              (ServerPlayerEntity) null,
              UltraShop.lang.getMessageShopRotated()
                .replace("%shop%", shop.getName()),
              UltraShop.lang.getPrefix(),
              TypeMessage.BROADCAST
            );
          }

          dynamicProduct.setTimeToUpdate(System.currentTimeMillis() + getCooldown(shop.getType()));

          List<Product> nuevosProductos = getNewProducts(shop);
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
    }

    if (shopType instanceof ShopTypeDynamicWeekly shopTypeDynamicWeekly) {
      return shopTypeDynamicWeekly.getProductsRotation();
    }

    return 3;
  }

  public long getCooldown(ShopType shopType) {

    DurationValue cooldown = null;

    if (shopType instanceof ShopTypeDynamic shopTypeDynamic) {
      cooldown = shopTypeDynamic.getCooldown();
    }

    if (shopType instanceof ShopTypeDynamicWeekly shopTypeDynamicWeekly) {
      cooldown = shopTypeDynamicWeekly.getCooldown();
    }

    return cooldown == null
      ? TimeUnit.MINUTES.toMillis(30)
      : cooldown.toMillis();
  }

  public List<Product> getNewProducts(Shop shop) {

    int productsRotation = getRotationProducts(shop);

    List<Product> products = new ArrayList<>(shop.getProducts());

    Collections.shuffle(products);

    if (products.size() > productsRotation) {
      return new ArrayList<>(products.subList(0, productsRotation));
    }

    return products;
  }

  public long getActualCooldown(Shop shop, ShopOptionsApi shopOptionsApi) {
    return products
      .computeIfAbsent(shopOptionsApi.getModId(), k -> new ConcurrentHashMap<>())
      .computeIfAbsent(shop.getId(), k -> new DynamicProduct())
      .getTimeToUpdate();
  }
}