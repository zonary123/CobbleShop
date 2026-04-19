package com.kingpixel.ultrashop.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the current rotation state for a dynamic shop.
 */
@Data
public class DynamicRotation {
  private long timeToUpdate;
  private List<Product> products = new ArrayList<>();
}

