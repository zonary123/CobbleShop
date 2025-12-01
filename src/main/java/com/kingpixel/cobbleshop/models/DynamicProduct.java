package com.kingpixel.cobbleshop.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 22/02/2025 4:13
 */
@Data
public class DynamicProduct {
  private long timeToUpdate;
  private List<Product> products = new ArrayList<>();
}
