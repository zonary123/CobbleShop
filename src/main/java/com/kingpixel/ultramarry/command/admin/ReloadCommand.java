package com.kingpixel.ultramarry.command.admin;

import com.kingpixel.ultramarry.UltraMarry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 *
 * @author Carlos Varas Alonso - 14/12/2025 0:40
 */
public class ReloadCommand {
  public static void register(LiteralArgumentBuilder<ServerCommandSource> base) {
    base.then(
      CommandManager.literal("reload")
        .requires(source -> source.hasPermissionLevel(4))
        .executes(context -> {
          UltraMarry.load();
          context.getSource().sendMessage(
            Text.literal(
              "§aUltraMarry configuration reloaded successfully."
            )
          );
          return 1;
        })
    );
  }
}
