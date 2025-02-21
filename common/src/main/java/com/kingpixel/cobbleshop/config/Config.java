package com.kingpixel.cobbleshop.config;

import com.kingpixel.cobbleutils.Model.PanelsConfig;
import lombok.Data;

import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Config {
  private String Path;
  // Essential fields
  private int rows;
  private String title;
  private String soundOpen;
  private String soundClose;
  private List<PanelsConfig> panels;

}
