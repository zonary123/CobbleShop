package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregated statistics for a single product within a shop.
 */
@Data
public class ProductStats {
  private final String shopId;
  private final String productId;
  private int totalBought;
  private int totalSold;
  private BigDecimal totalRevenue = BigDecimal.ZERO;
  private BigDecimal totalPayout = BigDecimal.ZERO;
  private final Set<UUID> uniqueBuyers = new HashSet<>();
  private final Set<UUID> uniqueSellers = new HashSet<>();

  public ProductStats(String shopId, String productId) {
    this.shopId = shopId;
    this.productId = productId;
  }

  /**
   * Accumulate a transaction into this stats bucket.
   */
  public void accumulate(Transaction tx) {
    if (tx.getAction() == ActionShop.BUY) {
      totalBought += tx.getAmount();
      totalRevenue = totalRevenue.add(tx.getValue());
      uniqueBuyers.add(tx.getPlayerUuid());
    } else {
      totalSold += tx.getAmount();
      totalPayout = totalPayout.add(tx.getValue());
      uniqueSellers.add(tx.getPlayerUuid());
    }
  }

  /**
   * Net profit for the server (revenue from buys minus payouts from sells).
   */
  public BigDecimal getNetProfit() {
    return totalRevenue.subtract(totalPayout);
  }

  /**
   * Total unique players (buyers + sellers).
   */
  public int getUniquePlayers() {
    Set<UUID> all = new HashSet<>(uniqueBuyers);
    all.addAll(uniqueSellers);
    return all.size();
  }
}

