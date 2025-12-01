package com.kingpixel.ultrashop.models;

import lombok.Data;

/**
 * @author Carlos Varas Alonso - 21/02/2025 19:22
 */
@Data
public class SubShop {
  private int slot;
  private String idShop;

  public SubShop(int slot, String idShop) {
    this.slot = slot;
    this.idShop = idShop;
  }
}
