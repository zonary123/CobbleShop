package com.kingpixel.cobblemarry.config;

import com.kingpixel.cobblemarry.CobbleMarry;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Lang {
  private String prefix;
  private String reload;
  private String symbolMarry;
  private String symbolNotMarry;
  private String messageGender;
  private String messageSendMarry;
  private String messageReceiveMarry;
  private String messageAcceptMarry;
  private String messageDenyMarry;
  private String messageNotHaveMarry;
  private String messageHaveMarry;
  private String messageTargetHaveMarry;
  private String messageDivorce;
  private String messageMarry;


  public Lang() {
    prefix = "<#4ddb93>[<#ebb35a>Marry<#4ddb93>] ";
    reload = "%prefix% <#ebb35a>Reloaded<#4ddb93>.";
    symbolMarry = "§c♥";
    symbolNotMarry = "§7\uD83D\uDC94";
    messageGender = "%prefix% <#ebb35a>%player%<#4ddb93> is now <#ebb35a>%gender%<#4ddb93>.";
    messageSendMarry = "%prefix% <#ebb35a>%player%<#4ddb93> has sent a marriage request to <#ebb35a>%target%<#4ddb93>.";
    messageReceiveMarry = "%prefix% <#ebb35a>%player%<#4ddb93> has sent you a marriage request. Use <#ebb35a>/marry accept<#4ddb93> to accept.";
    messageAcceptMarry = "%prefix% <#ebb35a>%player%<#4ddb93> has accepted your marriage request.";
    messageDenyMarry = "%prefix% <#ebb35a>%player%<#4ddb93> has denied your marriage request.";
    messageNotHaveMarry = "%prefix% <#ebb35a>%player%<#4ddb93> does not have a marriage request.";
    messageHaveMarry = "%prefix% <#ebb35a>%player%<#4ddb93> already has a marriage.";
    messageTargetHaveMarry = "%prefix% <#ebb35a>%player%<#4ddb93> already has a marriage.";
    messageDivorce = "%prefix% <#ebb35a>%player%<#4ddb93> has divorced.";
    messageMarry = "%prefix% <#ebb35a>%player%<#4ddb93> and <#ebb35a>%target%<#4ddb93> are now married.";

  }

  public void init() {
    File folder = Utils.getAbsolutePath(CobbleMarry.PATH_LANG);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(CobbleMarry.PATH_LANG, CobbleMarry.config.getLang() +
        ".json",
      call -> {
        CobbleMarry.lang = Utils.newGson().fromJson(call, Lang.class);
        CobbleMarry.lang.check();
        CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleMarry.PATH_LANG, CobbleMarry.config.getLang() + ".json",
          Utils.newGson().toJson(CobbleMarry.lang));
        if (!futureWrite.join()) {
          CobbleUtils.LOGGER.error("Error writing file: " + CobbleMarry.PATH_LANG + CobbleMarry.config.getLang() + ".json");
        }
      });

    if (!futureRead.join()) {
      CobbleMarry.lang = this;
      CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(CobbleMarry.PATH_LANG, CobbleMarry.config.getLang() + ".json",
        Utils.newGson().toJson(CobbleMarry.lang));
      if (!futureWrite.join()) {
        CobbleUtils.LOGGER.error("Error writing file: " + CobbleMarry.PATH_LANG + CobbleMarry.config.getLang() + ".json");
      }
    }
  }

  private void check() {
  }


}
