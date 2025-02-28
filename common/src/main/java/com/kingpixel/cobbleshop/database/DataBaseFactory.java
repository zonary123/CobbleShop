package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public class DataBaseFactory {
  public static DataBaseClient INSTANCE;

  public DataBaseFactory(DataBaseConfig config) {
    if (INSTANCE != null) {
      INSTANCE.disconnect();
    }
    switch (config.getType()) {
      case JSON -> INSTANCE = new DataBaseJSON(config);
      case MYSQL -> INSTANCE = new DataBaseMySQL(config);
      case SQLITE -> INSTANCE = new DataBaseSQLite(config);
      default -> throw new IllegalStateException("Unexpected value: " + config.getType());
    }
    INSTANCE.connect();
  }
}
