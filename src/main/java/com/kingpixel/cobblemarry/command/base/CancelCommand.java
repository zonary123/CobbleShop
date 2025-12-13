package com.kingpixel.cobblemarry.command.base;

import com.kingpixel.cobblemarry.command.CommandTree;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 *
 * @author Carlos Varas Alonso - 14/12/2025 0:11
 */
public class CancelCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      net.minecraft.server.command.CommandManager.literal("cancel")
        .executes(context -> {
          if (!context.getSource().isExecutedByPlayer()) return 0;
          // Simply remove any pending marriage request
          var player = context.getSource().getPlayer();
          if (player == null) return 0;
          CommandTree.removePendingMarriage(player.getUuid());
          player.sendMessage(
            Text.literal(
              "§aYou have cancelled any pending marriage requests."
            )
          );
          return 1;
        })
    );
  }
}
