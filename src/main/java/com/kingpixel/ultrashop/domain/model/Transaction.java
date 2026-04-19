package com.kingpixel.ultrashop.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records a single buy/sell transaction for auditing and analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
  private UUID playerUuid;
  private String playerName;
  private String shopId;
  private String productId;
  private ActionShop action;
  private int amount;
  private BigDecimal value;
  private String currency;
  private long timestamp;
}

