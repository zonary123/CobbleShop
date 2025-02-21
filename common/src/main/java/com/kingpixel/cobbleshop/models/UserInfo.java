package com.kingpixel.cobbleshop.models;

import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:31
 */
@Data
public class UserInfo {
  private UUID uuid;
  private String name;
  // Cooldown Product
  private Map<UUID, ProductLimit> cooldownProduct;
  // Transactions
  private List<ProductTransaction> transactions = new ArrayList<>();

  public UserInfo(ServerPlayerEntity player) {
    this.uuid = player.getUuid();
    this.name = player.getGameProfile().getName();
  }
}
