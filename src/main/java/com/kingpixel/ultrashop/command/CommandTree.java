package com.kingpixel.ultrashop.command;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.adapters.ShopType;
import com.kingpixel.ultrashop.adapters.ShopTypePermanent;
import com.kingpixel.ultrashop.api.ShopApi;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.config.Config;
import com.kingpixel.ultrashop.gui.edit.MenuEdit;
import com.kingpixel.ultrashop.models.Shop;
import com.kingpixel.ultrashop.models.TypeShop;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Stack;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:10
 */
public class CommandTree {
  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    LiteralArgumentBuilder<ServerCommandSource> base;

    for (String command : options.getCommands()) {
      if (options.getModId().equals(UltraShop.MOD_ID)) {
        base = build(CommandManager.literal(command), options);
      } else {
        base = CommandManager.literal(command)
          .then(
            build(CommandManager.literal("shop"), options)
          );
      }

      dispatcher.register(base);
    }

    // Sell
    dispatcher.register(
      CommandManager.literal("sell")
        .requires(source -> PermissionApi.hasPermission(source, "ultrashop.sell.base", 4))
        .then(
          CommandManager.literal("hand")
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              if (player == null) return 0;
              PlayerInventory inventory = player.getInventory();
              if (inventory == null) return 0;
              ShopApi.sellAll(context.getSource().getPlayer(), List.of(inventory.getMainHandStack()), options);
              return 1;
            }).then(
              CommandManager.argument("player", EntityArgumentType.player())
                .requires(source -> PermissionApi.hasPermission(source, "ultrashop.sell.other", 4))
                .executes(context -> {
                  ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                  ShopApi.sellAll(player, List.of(player.getMainHandStack()), options);
                  return 1;
                })
            )
        ).then(
          CommandManager.literal("all")
            .executes(context -> {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              ServerPlayerEntity player = context.getSource().getPlayer();
              if (player == null) return 0;
              PlayerInventory inventory = player.getInventory();
              if (inventory == null) return 0;
              ShopApi.sellAll(context.getSource().getPlayer(), inventory.main, options);
              return 1;
            }).then(
              CommandManager.argument("player", EntityArgumentType.player())
                .requires(source -> PermissionApi.hasPermission(source, "ultrashop.sell.other", 4))
                .executes(context -> {
                  ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
                  ShopApi.sellAll(player, player.getInventory().main, options);
                  return 1;
                })
            )
        )
    );


  }

  private static LiteralArgumentBuilder<ServerCommandSource> build(LiteralArgumentBuilder<ServerCommandSource> base, ShopOptionsApi options) {
    String modId = options.getModId().equals(UltraShop.MOD_ID) ? UltraShop.MOD_ID :
      options.getModId() + ".shop";
    base
      .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".base",
        modId + ".admin"), 2))
      .executes(context -> {
        if (!context.getSource().isExecutedByPlayer()) return 0;
        ShopApi.getConfig(options).open(context.getSource().getPlayer(), options);
        return 1;
      }).then(
        CommandManager.literal("reload")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".reload", modId + ".admin"), 2))
          .executes(context -> {
            try {
              if (!context.getSource().isExecutedByPlayer()) return 0;
              UltraShop.load(options);
              context.getSource().sendMessage(
                Text.literal("Reloaded " + options.getModId() + " shops")
              );
              return 1;
            } catch (Exception e) {
              e.printStackTrace();
              context.getSource().sendMessage(
                Text.literal("Error reloading " + options.getModId() + " shops")
              );
              return 0;
            }
          })
      ).then(
        CommandManager.literal("other")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".other", modId + ".admin"), 2))
          .then(
            CommandManager.argument("player", EntityArgumentType.players())
              .executes(context -> {
                var players = EntityArgumentType.getPlayers(context, "player");
                if (players.isEmpty()) return 0;
                var config = ShopApi.getConfig(options);
                for (ServerPlayerEntity player : players) {
                  config.open(player, options);
                }
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
                    openShop(context, options, true);
                    return 1;
                  }).then(
                    CommandManager.argument("WithClose", BoolArgumentType.bool())
                      .executes(context -> {
                        boolean withClose = BoolArgumentType.getBool(context, "WithClose");
                        openShop(context, options, withClose);
                        return 1;
                      })
                  )
              )
          )
      ).then(
        CommandManager.literal("edit")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".edit", modId + ".admin"), 4))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .suggests((commandContext, suggestionsBuilder) -> {
                for (Shop shop : ShopApi.getShops(options)) {
                  suggestionsBuilder.suggest(shop.getId());
                }
                return suggestionsBuilder.buildFuture();
              })
              .executes(context -> {
                if (!context.getSource().isExecutedByPlayer()) return 0;
                String idShop = StringArgumentType.getString(context, "shop");
                var shop = ShopApi.getShop(options, idShop);
                if (shop == null) return 0;
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                player.sendMessage(
                  Text.literal("This command is in development, please configure it in the json file"), false
                );
                MenuEdit.open(context.getSource().getPlayer(), shop, options);
                return 1;
              })
          )
      ).then(
        CommandManager.literal("create")
          .requires(source -> PermissionApi.hasPermission(source, List.of(modId + ".create", modId + ".admin"), 2))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .then(
                CommandManager.argument("type", StringArgumentType.string())
                  .suggests((commandContext, suggestionsBuilder) -> {
                    for (TypeShop value : TypeShop.values()) {
                      suggestionsBuilder.suggest(value.name());
                    }
                    return suggestionsBuilder.buildFuture();
                  })
                  .executes(context -> {
                    if (!context.getSource().isExecutedByPlayer()) return 0;
                    String id = StringArgumentType.getString(context, "shop");
                    boolean exist = ShopApi.getShops(options).stream().anyMatch(shop -> shop.getId().equals(id));
                    if (exist) {
                      context.getSource().sendMessage(
                        Text.literal("The shop already exists")
                      );
                      return 0;
                    }
                    Shop shop = new Shop(id, new ShopTypePermanent());
                    shop.setType(ShopType.get(StringArgumentType.getString(context, "type")));
                    ShopApi.getConfig(options).createShop(options, shop);
                    return 1;
                  })
              )
          )
      ).then(
        CommandManager.literal("restartShop")
          .requires(context -> PermissionApi.hasPermission(context, modId + ".restart.shop", 2))
          .then(
            CommandManager.argument("shop", StringArgumentType.string())
              .suggests((commandContext, suggestionsBuilder) -> {
                var shops = ShopApi.getShops(options);
                for (Shop shop : shops) {
                  var type = shop.getType().getTypeShop();
                  if (type == TypeShop.DYNAMIC || type == TypeShop.DYNAMIC_WEEKLY || type == TypeShop.DYNAMIC_CALENDAR)
                    suggestionsBuilder.suggest(shop.getId());
                }
                return suggestionsBuilder.buildFuture();
              })
              .executes(context -> {
                String shopId = StringArgumentType.getString(context, "shop");
                var shop = ShopApi.getShop(options, shopId);
                var type = shop.getType();
                switch (type.getTypeShop()) {
                  case DYNAMIC, DYNAMIC_WEEKLY, DYNAMIC_CALENDAR ->
                    UltraShop.dataShop.updateDynamicProducts(shop, options);
                  default -> context.getSource().sendMessage(
                    Text.literal("This shop is not dynamic, please use the command /shop reload")
                  );
                }
                return 1;
              })
          )
      );

    return base;
  }

  private static void openShop(CommandContext<ServerCommandSource> context, ShopOptionsApi options, boolean withClose) {
    try {
      var players = EntityArgumentType.getPlayers(context, "player");
      String s = StringArgumentType.getString(context, "IdShop");
      Config config = ShopApi.getConfig(options);
      Shop shop = ShopApi.getShop(options, s);
      if (shop == null) {
        for (ServerPlayerEntity player : players) {
          PlayerUtils.sendMessage(
            player,
            "The shop with id " + s + " does not exist",
            UltraShop.lang.getPrefix(),
            TypeMessage.CHAT
          );
        }
      } else {
        Stack<Shop> stack = new Stack<>();
        stack.push(shop);
        for (ServerPlayerEntity player : players) {
          shop.open(
            player,
            options,
            config,
            0,
            stack,
            withClose
          );
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


}
