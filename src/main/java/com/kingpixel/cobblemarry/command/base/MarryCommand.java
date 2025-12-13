package com.kingpixel.cobblemarry.command.base;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

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
              return 0;
            })
        )
    );
  }
}
