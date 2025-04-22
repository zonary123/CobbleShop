package com.kingpixel.cobbleshop.models;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.database.DataBaseFactory;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:31
 */
@Data
public class UserInfo {
  private UUID uuid;
  private String name;
  // Cooldown Product
  private Map<UUID, ProductLimit> cooldownProduct = new HashMap<>();
  // Transactions
  private List<ProductTransaction> transactions = new ArrayList<>();

  public UserInfo(ServerPlayerEntity player) {
    this.uuid = player.getUuid();
    this.name = player.getGameProfile().getName();
  }

  public void read(ServerPlayerEntity player) {
    File folder = Utils.getAbsolutePath(CobbleShop.PATH_DATA_USERS);

    if (!folder.exists()) {
      folder.mkdirs();
    }

    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(CobbleShop.PATH_DATA_USERS, player.getUuidAsString() + ".json",
      call -> {
        UserInfo userInfo = CobbleShop.gson.fromJson(call, UserInfo.class);
        userInfo.check(true);
        DataBaseFactory.users.put(player.getUuid(), userInfo);
      });

    if (!futureRead.join()) {
      String data = CobbleShop.gsonWithOutSpaces.toJson(this);
      Utils.writeFileAsync(Utils.getAbsolutePath(CobbleShop.PATH_DATA_USERS + player.getUuidAsString() + ".json"), data);
    }
  }

  public void check(boolean strong) {
    boolean save = false;
    if (strong) {
      if (!getCooldownProduct().isEmpty()) {
        List<UUID> toRemove = new ArrayList<>();
        getCooldownProduct().forEach((uuid, productLimit) -> {
          boolean exists = ShopApi.shops.values().stream()
            .flatMap(Collection::stream)
            .flatMap(shop -> shop.getProducts().stream())
            .anyMatch(product -> {
              UUID productUuid = product.getUuid();
              return productUuid != null && productUuid.equals(uuid);
            });
          if (!exists) {
            toRemove.add(uuid);
          }
        });
        toRemove.forEach(uuid -> getCooldownProduct().remove(uuid));
        if (!toRemove.isEmpty()) {
          save = true;
        }
      }
    }

    if (save) {
      DataBaseFactory.INSTANCE.updateUserInfo(this);
    }
  }

  public int getActualProductLimit(Product product) {
    ProductLimit productLimit = cooldownProduct.get(product.getUuid());
    if (productLimit == null) return 0;
    return productLimit.getAmount();
  }

  public void addProductLimit(Product product, int amount) {
    ProductLimit productLimit = cooldownProduct.get(product.getUuid());
    if (productLimit == null) {
      productLimit = new ProductLimit();
      productLimit.setUuid(product.getUuid());
      productLimit.setCooldown(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(product.getCooldown()));
      productLimit.setAmount(amount);
      cooldownProduct.put(product.getUuid(), productLimit);
    } else {
      productLimit.setAmount(productLimit.getAmount() + amount);
    }
    DataBaseFactory.INSTANCE.updateUserInfo(this);
  }

  public void write(ServerPlayerEntity player) {
    String data = CobbleShop.gsonWithOutSpaces.toJson(this);
    Utils.writeFileAsync(Utils.getAbsolutePath(CobbleShop.PATH_DATA_USERS + player.getUuidAsString() + ".json"), data);
  }
}
