package com.kingpixel.cobbleshop.models;

import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
@Builder
public class Shop {
  private String Path;
  // Essential fields
  private String id;
  private String title;
  private String currency;
  private String closeCommand;
  private String soundOpen;
  private String soundClose;
  private int rows;
  private int globalDiscount;
  private ShopType type;
  private Rectangle rectangle;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private ItemModel itemBalance;
  private List<Product> products;
  private ItemModel itemPrevious;
  private ItemModel itemClose;
  private ItemModel itemNext;
  private List<PanelsConfig> panels;
}
