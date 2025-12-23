package com.kingpixel.ultramarry.config;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.cobbleutils.Model.DurationValue;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultramarry.UltraMarry;
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
  private int amountParticles;
  private DurationValue cooldown;
  private List<String> commands;
  private Map<String, String> genders;
  private DataBaseConfig database;

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
    database.setDatabase("ultramarry");
  }

  public void init() {
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(UltraMarry.PATH, "config.json",
      call -> {
        UltraMarry.config = Utils.newGson().fromJson(call, Config.class);
        UltraMarry.config.check();
        CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(UltraMarry.PATH, "config.json",
          Utils.newGson().toJson(UltraMarry.config));
        if (Boolean.FALSE.equals(futureWrite.join())) {
          CobbleUtils.LOGGER.error("Error writing file: " + UltraMarry.PATH + "config.json");
        }
      });

    if (Boolean.FALSE.equals(futureRead.join())) {
      UltraMarry.config = this;
      CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(UltraMarry.PATH, "config.json",
        Utils.newGson().toJson(UltraMarry.config));
      if (Boolean.FALSE.equals(futureWrite.join())) {
        CobbleUtils.LOGGER.error("Error writing file: " + UltraMarry.PATH + "config.json");
      }
    }
  }

  private void check() {
  }


}
