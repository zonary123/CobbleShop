package com.kingpixel.ultramarry.models;

import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.util.Utils;
import com.kingpixel.ultramarry.UltraMarry;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;
import org.bson.Document;

import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 6:31
 */
@Data
public class UserInfo {
  private UUID uuid;
  private String playerName;
  private UUID marriedTo;
  private String marriedToName;
  private String gender;

  public UserInfo(ServerPlayerEntity player) {
    this.uuid = player.getUuid();
    this.playerName = player.getName().getString();
    this.gender = "none";
  }

  public UserInfo(UUID playerUUID) {
    var player = CobbleUtils.server.getPlayerManager().getPlayer(playerUUID);
    if (player != null) {
      this.uuid = player.getUuid();
      this.playerName = player.getName().getString();
      this.gender = "none";
    }
  }

  public void divorce() {
    this.marriedTo = null;
    this.marriedToName = null;
  }

  public void setMarriedTo(ServerPlayerEntity player) {
    this.marriedTo = player.getUuid();
    this.marriedToName = player.getGameProfile().getName();
  }

  public boolean isMarried() {
    return marriedTo != null;
  }


  public String obtainGender() {
    return UltraMarry.config.getGenders().getOrDefault(gender, CobbleUtils.language.getUnknown());
  }

  public String obtainMarry() {
    if (isMarried()) {
      return marriedToName;
    } else {
      return CobbleUtils.language.getNone();
    }
  }

  // MONGODB
  public static UserInfo fromDocument(Document document) {
    return Utils.newWithoutSpacingGson().fromJson(document.toJson(), UserInfo.class);
  }

  public Document toDocument() {
    var json = Utils.newWithoutSpacingGson().toJson(this);
    return Document.parse(json);
  }

  public boolean isMarriedTo(ServerPlayerEntity interact) {
    return isMarried() && marriedTo.equals(interact.getUuid());
  }
}
