package com.kingpixel.ultrashop.domain.model;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a price in a specific economy for a product.
 * Used for multi-currency products — most products use the simple buy/sell fields instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceEntry {
  private EconomyUse economy;
  private BigDecimal buy;
  private BigDecimal sell;

  /**
   * Whether this entry allows buying.
   */
  public boolean isBuyable() {
    return buy != null && buy.compareTo(BigDecimal.ZERO) > 0;
  }

  /**
   * Whether this entry allows selling.
   */
  public boolean isSellable() {
    return sell != null && sell.compareTo(BigDecimal.ZERO) > 0;
  }
}

