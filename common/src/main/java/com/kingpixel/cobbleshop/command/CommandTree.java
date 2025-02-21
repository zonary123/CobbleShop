package com.kingpixel.cobbleshop.command;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
public class CommandTree {
  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    LiteralArgumentBuilder<ServerCommandSource> base;
    for (String command : options.getCommands()) {
      if (options.getModId().equals(CobbleShop.MOD_ID)) {
        base = CommandManager.literal(command)
          .executes(context -> {
            if (!context.getSource().isExecutedByPlayer()) return 0;
            ShopApi.getConfig(options).open(context.getSource().getPlayer(), options);
            return 1;
          });
      } else {
        base = LiteralArgumentBuilder.literal(command);
      }

      dispatcher.register(base);
    }
  }
}
