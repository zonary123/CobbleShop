package com.kingpixel.cobbleshop.gui;

import com.kingpixel.cobbleshop.models.ActionShop;
import com.kingpixel.cobbleshop.models.Product;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:54
 */
public class MenuBuyAndSell {
  private int rows;
  private String titleBuy;
  private String titleSell;
  private List<PanelsConfig> panels;


  public void open(ServerPlayerEntity player, Product product, int amount, ActionShop actionShop) {
  }
}
