package com.kingpixel.cobblemarry.command.base;

import com.kingpixel.cobblemarry.database.DataBaseFactory;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 *
 * @author Carlos Varas Alonso - 13/12/2025 5:38
 */
public class DivorceCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      net.minecraft.server.command.CommandManager.literal("divorce")
        .executes(context -> {
          if (!context.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = context.getSource().getPlayer();
          if (player == null) return 0;
          DataBaseFactory.INSTANCE.divorce(player.getUuid());
          return 1;
        })
    );
  }
}
