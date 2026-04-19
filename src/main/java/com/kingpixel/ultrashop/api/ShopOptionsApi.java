package com.kingpixel.ultrashop.api;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Options that identify a mod's shop registration.
 * Other mods pass this to {@link ShopApi#register} to set up their shops.
 */
@Data
@Builder
public class ShopOptionsApi {
  private String modId;
  private String path;
  @Builder.Default
  private List<String> commands = new ArrayList<>();

  public ShopOptionsApi(String modId, String path, List<String> commands) {
    this.modId = modId;
    this.path = path;
    this.commands = commands != null ? commands : new ArrayList<>();
  }

  /**
   * Returns the path to the shop directory.
   */
  public String getPathShop() {
    return path + "shop/";
  }
}
