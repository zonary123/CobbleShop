package com.kingpixel.ultramarry.command.base;

import com.kingpixel.ultramarry.UltraMarry;
import com.kingpixel.ultramarry.command.CommandTree;
import com.kingpixel.ultramarry.database.DataBaseFactory;
import com.kingpixel.ultramarry.models.UserInfo;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 *
 * @author Carlos Varas Alonso - 14/12/2025 0:11
 */
public class AcceptCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("accept")
        .executes(context -> {
          if (!context.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = context.getSource().getPlayer();
          if (player == null) return 0;
          UUID pedingUUID = CommandTree.getPendingMarriage(player.getUuid());
          if (pedingUUID == null) {
            // No pending marriage
            player.sendMessage(
              net.minecraft.text.Text.literal(
                "§cYou have no pending marriage requests."
              )
            );
            return 1;
          }
          UserInfo pedingUserInfo = DataBaseFactory.INSTANCE.getUserInfo(pedingUUID);
          if (pedingUserInfo == null) return 0;
          UltraMarry.runAsync(() -> DataBaseFactory.INSTANCE.marry(player.getUuid(), pedingUUID));

          return 1;
        })
    );
  }
}
