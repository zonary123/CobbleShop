package com.kingpixel.ultrashop.models;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:32
 */
@Data
public class ProductTransaction {
  private String product;
  private BigDecimal buyPrice;
  private BigDecimal buyAmount;
  private BigDecimal sellPrice;
  private BigDecimal sellAmount;
}
