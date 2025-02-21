package com.kingpixel.cobbleshop.migrate;

import com.kingpixel.cobbleutils.Model.ItemModel;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:24
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Data
@ToString
public class FillItems extends ItemModel {
  private List<Integer> slots;

  public FillItems() {
    super("minecraft:gray_stained_glass_pane");
    slots = new ArrayList<>();
  }
}