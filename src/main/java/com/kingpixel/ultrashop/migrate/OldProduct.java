package com.kingpixel.ultrashop.migrate;

import com.kingpixel.ultrashop.models.Product;
import lombok.*;
import net.minecraft.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 16/09/2024 18:43
 */
@Getter
@Setter
@EqualsAndHashCode
@Data
@ToString
public class OldProduct {
  // Optional
  private Boolean notCanBuyWithPermission;
  private String permission;
  private String color;
  private String display;
  private String displayname;
  private List<String> lore;
  private Integer CustomModelData;
  private Integer discount;
  // Always have date
  private String product;
  private BigDecimal buy;
  private BigDecimal sell;

  public OldProduct() {
    this.notCanBuyWithPermission = null;
    this.display = null;
    this.color = null;
    this.displayname = null;
    this.lore = null;
    this.CustomModelData = null;
    this.permission = null;
    this.discount = null;
    this.product = "minecraft:stone";
    this.buy = BigDecimal.valueOf(500000);
    this.sell = BigDecimal.ZERO;
  }

  public OldProduct(boolean optional) {
    if (optional) {
      this.notCanBuyWithPermission = true;
      this.display = "minecraft:dirt";
      this.color = "<#e7af76>";
      this.displayname = "Custom Dirt";
      this.lore = List.of("This is a custom dirt", "You can use it to build");
      this.CustomModelData = 0;
      this.permission = "cobbleutils.dirt";
      this.discount = 10;
    } else {
      this.notCanBuyWithPermission = null;
      this.display = null;
      this.color = null;
      this.displayname = null;
      this.lore = null;
      this.CustomModelData = null;
      this.permission = null;
      this.discount = null;
    }
    this.product = "minecraft:stone";
    this.buy = BigDecimal.valueOf(500000);
    this.sell = BigDecimal.ZERO;
  }

  public OldProduct(ItemStack defaultStack) {
    this.notCanBuyWithPermission = null;
    this.display = null;
    this.color = null;
    this.displayname = null;
    this.lore = null;
    this.CustomModelData = null;
    this.permission = null;
    this.discount = null;
    this.product = defaultStack.getItem().getTranslationKey()
      .replace("item.", "")
      .replace("block.", "")
      .replace(".", ":");
    this.buy = BigDecimal.valueOf(500000);
    this.sell = BigDecimal.ZERO;
  }

  public Product from() {
    Product product = new Product();
    product.setProduct(this.product);
    product.setBuy(this.buy);
    product.setSell(this.sell);
    product.setDiscount((float) (this.discount == null ? 0 : this.discount));
    product.setDisplay(this.display);
    product.setDisplayname(this.displayname);
    product.setLore(this.lore);
    product.setCustomModelData(this.CustomModelData);
    product.setSlot(0);
    product.setCanBuyPermission(this.permission);
    return product;
  }
}
