package com.kingpixel.ultramarry.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kingpixel.ultramarry.UltraMarry;
import com.kingpixel.ultramarry.command.admin.ReloadCommand;
import com.kingpixel.ultramarry.command.base.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lombok.Data;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
@Data
public class CommandTree {
  // Target UUID -> Requester UUID
  private static final Cache<UUID, UUID> PENDING_MARRIAGE = Caffeine.newBuilder()
    .expireAfterWrite(30, TimeUnit.SECONDS)
    .build();

  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess, CommandManager.RegistrationEnvironment registrationEnvironment) {

    for (String command : UltraMarry.config.getCommands()) {
      LiteralArgumentBuilder<ServerCommandSource> base = CommandManager.literal(command);
      DivorceCommand.register(base);
      GenderCommand.register(base);
      MarryCommand.register(base);
      CancelCommand.register(base);
      AcceptCommand.register(base);
      ReloadCommand.register(base);
      dispatcher.register(base);
    }
  }

  public static void addPendingMarriage(UUID from, UUID to) {
    PENDING_MARRIAGE.put(to, from);
  }

  public static UUID getPendingMarriage(UUID to) {
    return PENDING_MARRIAGE.getIfPresent(to);
  }

  public static void removePendingMarriage(UUID to) {
    PENDING_MARRIAGE.invalidate(to);
  }
}
