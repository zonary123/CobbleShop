package com.kingpixel.cobbleshop.migrate;

import ca.landonjw.gooeylibs2.api.template.TemplateType;
import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleshop.adapters.ShopTypePermanent;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.Rectangle;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
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
  private TemplateType templateType;
  private ShopType shopType;
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
  private List<Integer> slotsPrevious;
  private List<Integer> slotsClose;
  private List<Integer> slotsNext;
  //private ItemModel money;
  private ItemModel fill;
  private List<FillItems> fillItems;

  public OldShop() {
    this.active = true;
    this.id = "";
    this.title = "";
    this.rows = 3;
    this.currency = "dollars";
    this.templateType = TemplateType.CHEST;
    this.rectangle = new Rectangle();
    this.display = new ItemModel("cobblemon:poke_ball");
    this.itemInfoShop = display;
    this.slotbalance = 47;
    this.globalDiscount = 0;
    this.soundopen = "cobblemon:pc.on";
    this.soundclose = "cobblemon:pc.off";
    this.colorItem = "<#6bd68f>";
    this.closeCommand = "";
    this.close = CobbleUtils.language.getItemClose();
    close.setSlot(49);
    this.next = CobbleUtils.language.getItemNext();
    next.setSlot(53);
    this.previous = CobbleUtils.language.getItemPrevious();
    previous.setSlot(45);
    this.products = getDefaultProducts();
    this.fill = new ItemModel("");
    this.fillItems = new ArrayList<>();
    this.fillItems.add(new FillItems());
    this.shopType = new ShopTypePermanent();
  }

  public OldShop(String id, String title, ShopType shopType, short rows, List<String> lore) {
    this.active = true;
    this.id = id;
    this.title = title;
    this.rows = rows;
    this.slotbalance = 47;
    this.slotsNext = new ArrayList<>();
    this.slotsPrevious = new ArrayList<>();
    this.slotsClose = new ArrayList<>();
    this.soundopen = "cobblemon:pc.on";
    this.soundclose = "cobblemon:pc.off";
    this.currency = "dollars";
    this.templateType = TemplateType.CHEST;
    this.rectangle = new Rectangle();
    this.shopType = shopType;
    this.colorItem = "<#6bd68f>";
    this.closeCommand = "";
    this.close = CobbleUtils.language.getItemClose();
    close.setSlot(49);
    this.next = CobbleUtils.language.getItemNext();
    next.setSlot(53);
    this.previous = CobbleUtils.language.getItemPrevious();
    previous.setSlot(45);
    this.globalDiscount = 0;
    this.display = new ItemModel("cobblemon:poke_ball");
    display.setDisplayname(title);
    display.setLore(lore);
    this.itemInfoShop = display;
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
