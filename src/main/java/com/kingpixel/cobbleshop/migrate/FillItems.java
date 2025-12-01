package com.kingpixel.cobbleshop.migrate;

import com.kingpixel.cobbleutils.Model.ItemModel;
import lombok.*;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:24
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@ToString
public class FillItems extends ItemModel {

  public FillItems() {
    super("minecraft:gray_stained_glass_pane");
  }
}