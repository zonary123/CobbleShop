package com.kingpixel.cobbleshop.command;

import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleshop.models.Shop;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
public class CommandTree {
  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    LiteralArgumentBuilder<ServerCommandSource> base;

    LiteralArgumentBuilder<ServerCommandSource> overBase = null;

    for (String command : options.getCommands()) {
      if (options.getModId().equals(CobbleShop.MOD_ID)) {
        base = build(CommandManager.literal(command), options);
      } else {
        base = CommandManager.literal(command)
          .then(
            build(CommandManager.literal("shop"), options)
          );
      }

      dispatcher.register(base);
    }
  }

  private static LiteralArgumentBuilder<ServerCommandSource> build(LiteralArgumentBuilder<ServerCommandSource> base, ShopOptionsApi options) {
    base.executes(context -> {
      if (!context.getSource().isExecutedByPlayer()) return 0;
      ShopApi.getConfig(options).open(context.getSource().getPlayer(), options);
      return 1;
    }).then(
      CommandManager.literal("reload")
        .executes(context -> {
          if (!context.getSource().isExecutedByPlayer()) return 0;
          CobbleShop.load(options);
          return 1;
        })
    ).then(
      CommandManager.literal("other")
        .then(
          CommandManager.argument("player", EntityArgumentType.player())
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
              ShopApi.getConfig(options).open(player, options);
              return 1;
            }).then(
              CommandManager.argument("IdShop", StringArgumentType.string())
                .suggests((context, builder) -> {
                  for (Shop shop : ShopApi.getShops(options)) {
                    builder.suggest(shop.getId());
                  }
                  return builder.buildFuture();
                })
                .executes(context -> {
                  if (!context.getSource().isExecutedByPlayer()) return 0;
                  ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                  String shop = StringArgumentType.getString(context, "IdShop");
                  Config config = ShopApi.getConfig(options);
                  ShopApi.getShop(options, shop).open(
                    player,
                    options,
                    config,
                    0,
                    null
                  );
                  return 1;
                })
            )
        )
    );

    return base;
  }


}
