package com.kingpixel.cobblemarry.config;

import com.kingpixel.cobblemarry.CobbleMarry;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Config {
  // Essential fields
  private boolean debug;
  private String lang;
  private List<String> commands;
  private Map<String, String> genders;
  private DataBaseConfig database;
  private int cooldown;

  public Config() {
    this.debug = false;
    this.lang = "en";
    this.commands = List.of(
      "marry"
    );
    this.genders = Map.of(
      "", "",
      "male", "♂",
      "female", "♀",
      "other", "⚥"
    );
    this.database = new DataBaseConfig();
    database.setDatabase("cobblemarry");
    cooldown = 15;
  }

  public void init() {
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(CobbleMarry.PATH, "config.json",
      call -> {
        CobbleMarry.config = Utils.newGson().fromJson(call, Config.class);
        CobbleMarry.config.check();
        CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleMarry.PATH, "config.json",
          Utils.newGson().toJson(CobbleMarry.config));
        if (!futureWrite.join()) {
          CobbleUtils.LOGGER.error("Error writing file: " + CobbleMarry.PATH + "config.json");
        }
      });

    if (!futureRead.join()) {
      CobbleMarry.config = this;
      CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleMarry.PATH, "config.json",
        Utils.newGson().toJson(CobbleMarry.config));
      if (!futureWrite.join()) {
        CobbleUtils.LOGGER.error("Error writing file: " + CobbleMarry.PATH + "config.json");
      }
    }
  }

  private void check() {
  }


}
