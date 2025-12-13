package com.kingpixel.cobblemarry.command.base;

import com.kingpixel.cobblemarry.command.CommandTree;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 *
 * @author Carlos Varas Alonso - 13/12/2025 5:38
 */
public class MarryCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("marry")
        .then(
          CommandManager.argument("player", EntityArgumentType.player())
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
              UUID targetUUID = target.getUuid();
              if (player == null) return 0;
              UUID playerUUID = player.getUuid();
              if (playerUUID.equals(targetUUID)) {
                // Can't marry yourself
                player.sendMessage(
                  Text.literal(
                    "§cYou cannot marry yourself."
                  )
                );
                return 1;
              }
              var pending = CommandTree.getPendingMarriage(targetUUID);
              if (pending != null && pending.equals(playerUUID)) {
                // They have already sent a request
                player.sendMessage(
                  Text.literal(
                    "§cYou have already sent a marriage request to " + target.getDisplayName().getString() + "§c. Please wait for them to accept it."
                  )
                );
                return 1;
              }
              CommandTree.addPendingMarriage(playerUUID, targetUUID);
              player.sendMessage(
                Text.literal(
                  "§aYou have sent a marriage request to " + target.getDisplayName().getString() + "§a. They have 30 seconds to accept it."
                )
              );
              target.sendMessage(
                Text.literal(
                  "§e" + player.getDisplayName().getString() + "§e has sent you a marriage request. Type §a/marry accept§e to accept it or §c/marry cancel§c to decline it."
                )
              );
              return 1;
            })
        )
    );
  }
}
