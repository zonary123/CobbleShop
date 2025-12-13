package com.kingpixel.cobblemarry.command;

import com.kingpixel.cobblemarry.CobbleMarry;
import com.kingpixel.cobblemarry.command.base.DivorceCommand;
import com.kingpixel.cobblemarry.command.base.GenderCommand;
import com.kingpixel.cobblemarry.command.base.MarryCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Data;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
@Data
public class CommandTree {
  private static Map<UUID, UUID> marrys = new HashMap<>();
  private static Map<UUID, Long> cooldown = new HashMap<>();

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {

    for (String command : CobbleMarry.config.getCommands()) {
      LiteralArgumentBuilder<ServerCommandSource> base = CommandManager.literal(command);
      DivorceCommand.register(base);
      GenderCommand.register(base);
      MarryCommand.register(base);
    }
  }

}
