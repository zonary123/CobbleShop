package com.kingpixel.cobbleshop.models;

import com.kingpixel.cobbleshop.config.Config;
import net.minecraft.server.network.ServerPlayerEntity;

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

  public void purchase(ServerPlayerEntity player, Product product, Shop shop, int amount, Config config) {
    if (this.product.equals(product.getProduct())) {
      this.amountBuy = this.amountBuy.add(BigDecimal.valueOf(amount));
      this.buy = this.buy.add(product.getBuy().add(product.getBuyPrice(player, amount, shop, config)));
    }
  }

  public void sell(Product product, int amount) {
    if (this.product.equals(product.getProduct())) {
      this.amountSell = this.amountSell.add(BigDecimal.valueOf(amount));
      this.sell = this.sell.add(product.getSellPrice(amount));
    }
  }
}
