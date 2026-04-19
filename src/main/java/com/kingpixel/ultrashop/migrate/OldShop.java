package com.kingpixel.ultrashop.migrate;

import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy v0 shop format. Kept ONLY for migration support.
 *
 * @author Carlos Varas Alonso - 02/08/2024 9:25
 */
@Getter
@Setter
@EqualsAndHashCode
@Data
@ToString
public class OldShop {
  private boolean active;
  private String id;
  private String title;
  private short rows;
  private String currency;
  private Rectangle rectangle;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private short slotbalance;
  private int globalDiscount;
  private String soundopen;
  private String soundclose;
  private String colorItem;
  private ItemModel previous;
  private String closeCommand;
  private ItemModel close;
  private ItemModel next;
  private List<OldProduct> products;
  private ItemModel fill;
  private List<FillItems> fillItems;

  public OldShop() {
    this.active = true;
    this.id = "";
    this.title = "";
    this.rows = 3;
    this.currency = "dollars";
    this.rectangle = new Rectangle();
    this.display = new ItemModel("cobblemon:poke_ball");
    this.itemInfoShop = display;
    this.slotbalance = 47;
    this.globalDiscount = 0;
    this.soundopen = "cobblemon:pc.on";
    this.soundclose = "cobblemon:pc.off";
    this.colorItem = "<#6bd68f>";
    this.closeCommand = "";
    this.close = new ItemModel("minecraft:barrier");
    close.setSlot(49);
    this.next = new ItemModel("minecraft:arrow");
    next.setSlot(53);
    this.previous = new ItemModel("minecraft:arrow");
    previous.setSlot(45);
    this.products = getDefaultProducts();
    this.fill = new ItemModel("");
    this.fillItems = new ArrayList<>();
    this.fillItems.add(new FillItems());
  }

  public static List<OldProduct> getDefaultProducts() {
    List<OldProduct> products = new ArrayList<>();
    ItemChance.defaultItemChances().forEach(itemChance -> {
      OldProduct product = new OldProduct();
      product.setProduct(itemChance.getItem());
      product.setBuy(BigDecimal.valueOf(100));
      product.setSell(BigDecimal.valueOf(25));
      products.add(product);
    });
    products.add(new OldProduct(true));
    return products;
  }
}
