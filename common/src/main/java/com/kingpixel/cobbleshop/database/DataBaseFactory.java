package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleshop.models.UserInfo;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public class DataBaseFactory {
  public static DataBaseClient INSTANCE;
  public static final Map<UUID, UserInfo> users = new HashMap<>();

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
