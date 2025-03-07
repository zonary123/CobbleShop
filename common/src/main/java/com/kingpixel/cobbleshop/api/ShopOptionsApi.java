package com.kingpixel.cobbleshop.api;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:15
 */
@Data
@Builder
public class ShopOptionsApi {
  private String modId;
  private String path;
  private List<String> commands;


  public ShopOptionsApi(String modId, String path, List<String> commands) {
    this.modId = modId;
    this.path = path;
    this.commands = commands;
  }

  public String getPathShop() {
    return path + "shop/";
  }

}
