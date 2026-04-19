package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

/**
 * A reference to a sub-shop (category) within a parent shop.
 */
@Data
public class SubShop {
  private int slot;
  private String idShop;

  public SubShop() {
  }

  public SubShop(int slot, String idShop) {
    this.slot = slot;
    this.idShop = idShop;
  }
}

