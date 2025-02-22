package com.kingpixel.cobbleshop.database;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;

/**
 * @author Carlos Varas Alonso - 22/02/2025 3:52
 */
public class DataBaseFactory {
  public DataBaseClient client;

  public DataBaseFactory(DataBaseConfig config) {
    if (client != null) {
      client.disconnect();
    }
    switch (config.getType()) {
      case JSON -> client = new DataBaseJSON(config);
      case MYSQL -> client = new DataBaseMySQL(config);
      case SQLITE -> client = new DataBaseSQLite(config);
    }
    client.connect();
  }

  public void updateDynamicShop() {

  }
}
