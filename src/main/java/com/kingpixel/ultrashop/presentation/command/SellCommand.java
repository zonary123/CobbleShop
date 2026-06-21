package com.kingpixel.ultrashop.presentation.command;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.service.TransactionService;
import com.kingpixel.ultrashop.presentation.gui.SellGuiBuilder;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * /sell hand and /sell all commands — extracted for SRP.
 * Command aliases are configurable via config.json sellCommands.
 */
public final class SellCommand {

  private SellCommand() {
  }

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
    List<String> aliases = config != null && config.getSellCommands() != null && !config.getSellCommands().isEmpty()
      ? config.getSellCommands()
      : List.of("sell");

    for (String alias : aliases) {
      dispatcher.register(buildSellCommand(CommandManager.literal(alias), options));
    }
  }

  private static LiteralArgumentBuilder<ServerCommandSource> buildSellCommand(
    LiteralArgumentBuilder<ServerCommandSource> base, ShopOptionsApi options) {

    return base
      .requires(src -> PermissionApi.hasPermission(src, "ultrashop.sell.base", 4))
      .executes(ctx -> {
        if (!ctx.getSource().isExecutedByPlayer()) return 0;
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        SellGuiBuilder.open(player);
        return 1;
      })

      // /sell gui
      .then(CommandManager.literal("gui")
        .executes(ctx -> {
          if (!ctx.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          SellGuiBuilder.open(player);
          return 1;
        }))

      // /sell hand
      .then(CommandManager.literal("hand")
        .executes(ctx -> {
          if (!ctx.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          TransactionService.sellAll(player, List.of(player.getMainHandStack()));
          return 1;
        })
        .then(CommandManager.argument("player", EntityArgumentType.player())
          .requires(src -> PermissionApi.hasPermission(src, "ultrashop.sell.other", 4))
          .executes(ctx -> {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
            TransactionService.sellAll(player, List.of(player.getMainHandStack()));
            return 1;
          })))

      // /sell all
      .then(CommandManager.literal("all")
        .executes(ctx -> {
          if (!ctx.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          TransactionService.sellAll(player, player.getInventory().main);
          return 1;
        })
        .then(CommandManager.argument("player", EntityArgumentType.player())
          .requires(src -> PermissionApi.hasPermission(src, "ultrashop.sell.other", 4))
          .executes(ctx -> {
            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
            TransactionService.sellAll(player, player.getInventory().main);
            return 1;
          })));
  }
}

