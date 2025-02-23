package com.kingpixel.cobbleshop.models;

import java.math.BigDecimal;

/**
 * @author Carlos Varas Alonso - 22/02/2025 7:01
 */
public class DataProduct {
  private String product;
  private BigDecimal amountBuy;
  private BigDecimal buy;
  private BigDecimal amountSell;
  private BigDecimal sell;


  public DataProduct from(Product product) {
    this.product = product.getProduct();
    this.buy = BigDecimal.ZERO;
    this.sell = BigDecimal.ZERO;
    return this;
  }

  public void purchase(Product product, Shop shop, int amount) {
    if (this.product.equals(product.getProduct())) {
      this.amountBuy = this.amountBuy.add(BigDecimal.valueOf(amount));
      this.buy = this.buy.add(product.getBuy().add(product.getBuyPrice(amount, shop)));
    }
  }

  public void sell(Product product, int amount) {
    if (this.product.equals(product.getProduct())) {
      this.amountSell = this.amountSell.add(BigDecimal.valueOf(amount));
      this.sell = this.sell.add(product.getSellPrice(amount));
    }
  }
}
