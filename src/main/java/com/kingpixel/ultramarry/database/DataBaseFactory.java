package com.kingpixel.ultramarry.database;

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
      case JSON -> INSTANCE = new DataBaseJSON();
      case SQLITE, MYSQL -> INSTANCE = new DataBaseSQL();
      case MONGODB -> INSTANCE = new DataBaseMONGODB();
    }
    INSTANCE.connect();
  }

}
