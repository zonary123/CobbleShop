package com.kingpixel.ultrashop.presentation.command;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.service.StatsService;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.presentation.gui.MainMenuBuilder;
import com.kingpixel.ultrashop.presentation.gui.NavigationContext;
import com.kingpixel.ultrashop.presentation.gui.ShopMenuBuilder;
import com.kingpixel.ultrashop.presentation.gui.StatsMenuBuilder;
import com.kingpixel.ultrashop.presentation.gui.TransactionMenuBuilder;
import com.kingpixel.ultrashop.presentation.gui.edit.ShopEditMenuBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Command tree registration — clean delegation to services and GUI builders.
 */
public final class CommandTree {

  private CommandTree() {
  }

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    for (String command : options.getCommands()) {
      LiteralArgumentBuilder<ServerCommandSource> base;
      if (options.getModId().equals(UltraShop.MOD_ID)) {
        base = build(CommandManager.literal(command), options);
      } else {
        base = CommandManager.literal(command)
          .then(build(CommandManager.literal("shop"), options));
      }
      dispatcher.register(base);
    }

    // /sell hand & /sell all
    SellCommand.register(options, dispatcher);
    // /<cmd> search <query>
    SearchCommand.register(options, dispatcher);
  }

  private static LiteralArgumentBuilder<ServerCommandSource> build(
    LiteralArgumentBuilder<ServerCommandSource> base, ShopOptionsApi options) {

    String modId = options.getModId().equals(UltraShop.MOD_ID)
      ? UltraShop.MOD_ID : options.getModId() + ".shop";

    return base
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".base", modId + ".admin"), 2))

      // /shop — open main menu
      .executes(ctx -> {
        if (!ctx.getSource().isExecutedByPlayer()) return 0;
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
        MainMenuBuilder.open(player, config, options.getModId());
        return 1;
      })

      // /shop reload
      .then(CommandManager.literal("reload")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".reload", modId + ".admin"), 2))
        .executes(ctx -> {
          ConfigLoader.load(options);
          StatsService.invalidateCache();
          ShopContext.get().startDashboard();
          ctx.getSource().sendMessage(Text.literal("Reloaded " + options.getModId() + " shops"));
          return 1;
        }))

      // /shop other <player> [shopId] [withClose]
      .then(CommandManager.literal("other")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".admin"), 2))
        .then(CommandManager.argument("player", EntityArgumentType.players())
          .executes(ctx -> {
            ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
            for (ServerPlayerEntity p : EntityArgumentType.getPlayers(ctx, "player")) {
              MainMenuBuilder.open(p, config, options.getModId());
            }
            return 1;
          })
          .then(CommandManager.argument("IdShop", StringArgumentType.string())
            .suggests((ctx, builder) -> {
              ShopContext.get().getShops(options.getModId()).forEach(s -> builder.suggest(s.getId()));
              return builder.buildFuture();
            })
            .executes(ctx -> openShopForPlayers(ctx, options, true))
            .then(CommandManager.argument("WithClose", BoolArgumentType.bool())
              .executes(ctx -> openShopForPlayers(ctx, options, BoolArgumentType.getBool(ctx, "WithClose")))))))

      // /shop create <name>
      .then(CommandManager.literal("create")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".admin"), 2))
        .then(CommandManager.argument("shop", StringArgumentType.string())
          .then(CommandManager.argument("dynamic", BoolArgumentType.bool())
            .executes(ctx -> {
              String id = StringArgumentType.getString(ctx, "shop");
              boolean exists = ShopContext.get().getShops(options.getModId()).stream()
                .anyMatch(s -> s.getId().equals(id));
              if (exists) {
                ctx.getSource().sendMessage(Text.literal("Shop already exists: " + id));
                return 0;
              }
              boolean dynamic = BoolArgumentType.getBool(ctx, "dynamic");
              Shop shop = new Shop(id, dynamic);
              ConfigLoader.createShop(options, shop);
              ctx.getSource().sendMessage(Text.literal("Created shop: " + id));
              return 1;
            }))))

      // /shop restartShop <shopId>
      .then(CommandManager.literal("restartShop")
        .requires(src -> PermissionApi.hasPermission(src, modId + ".restart.shop", 2))
        .then(CommandManager.argument("shop", StringArgumentType.string())
          .suggests((ctx, builder) -> {
            ShopContext.get().getShops(options.getModId()).stream()
              .filter(s -> s.getRotationSchedule() != null)
              .forEach(s -> builder.suggest(s.getId()));
            return builder.buildFuture();
          })
          .executes(ctx -> {
            String shopId = StringArgumentType.getString(ctx, "shop");
            Shop shop = ShopContext.get().getShops(options.getModId()).stream()
              .filter(s -> s.getId().equals(shopId))
              .findFirst().orElse(null);
            if (shop != null && shop.getRotationSchedule() != null) {
              ShopContext.get().getDataShop().updateDynamicProducts(shop, options.getModId(), true);
              ctx.getSource().sendMessage(Text.literal("Restarted dynamic shop: " + shopId));
            } else {
              ctx.getSource().sendMessage(Text.literal("Shop is not dynamic or not found: " + shopId));
            }
            return 1;
          })))

      // /shop transactions [player]
      .then(CommandManager.literal("transactions")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".transactions", modId + ".admin"), 2))
        .executes(ctx -> {
          if (!ctx.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
          TransactionMenuBuilder.open(player, player.getUuid(), player.getGameProfile().getName(),
            config, options.getModId());
          return 1;
        })
        .then(CommandManager.argument("player", EntityArgumentType.player())
          .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".admin"), 2))
          .executes(ctx -> {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            if (ctx.getSource().isExecutedByPlayer()) {
              ServerPlayerEntity viewer = ctx.getSource().getPlayer();
              if (viewer != null) {
                ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
                TransactionMenuBuilder.open(viewer, target.getUuid(), target.getGameProfile().getName(),
                  config, options.getModId());
                return 1;
              }
            }
            return showTransactions(ctx.getSource(), target.getUuid(), target.getGameProfile().getName(), options);
          })))

      // /shop delete <shopId>
      .then(CommandManager.literal("delete")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".admin"), 2))
        .then(CommandManager.argument("shop", StringArgumentType.string())
          .suggests((ctx, builder) -> {
            ShopContext.get().getShops(options.getModId()).forEach(s -> builder.suggest(s.getId()));
            return builder.buildFuture();
          })
          .executes(ctx -> {
            String shopId = StringArgumentType.getString(ctx, "shop");
            Shop shop = ShopContext.get().getShops(options.getModId()).stream()
              .filter(s -> s.getId().equals(shopId))
              .findFirst().orElse(null);
            if (shop == null) {
              ctx.getSource().sendMessage(Text.literal("§cShop not found: " + shopId));
              return 0;
            }
            try {
              if (shop.getFilePath() != null) {
                Files.deleteIfExists(Path.of(shop.getFilePath()));
              }
              ShopContext.get().getShops(options.getModId()).remove(shop);
              ShopContext.get().getSellIndex().rebuild(ShopContext.get().getShops());
              ctx.getSource().sendMessage(Text.literal("§aDeleted shop: " + shopId));
            } catch (Exception e) {
              ctx.getSource().sendMessage(Text.literal("§cError deleting shop: " + e.getMessage()));
            }
            return 1;
          })))

      // /shop edit — open admin edit GUI
      .then(CommandManager.literal("edit")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".admin"), 2))
        .executes(ctx -> {
          if (!ctx.getSource().isExecutedByPlayer()) return 0;
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
          ShopEditMenuBuilder.openShopList(player, config, options.getModId());
          return 1;
        }))

      // /shop stats — open stats GUI (admin) or print to console
      .then(CommandManager.literal("stats")
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".stats", modId + ".admin"), 2))
        .executes(ctx -> {
          if (ctx.getSource().isExecutedByPlayer()) {
            ServerPlayerEntity player = ctx.getSource().getPlayer();
            if (player == null) return 0;
            ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
            StatsMenuBuilder.open(player, config, options.getModId());
          } else {
            // Console: print text summary
            var totals = StatsService.getServerTotals(30);
            ctx.getSource().sendMessage(Text.literal(
              "§6--- UltraShop Stats (30d) ---\n" +
              "§7Transactions: §f" + totals.totalTransactions + "\n" +
              "§7Players: §f" + totals.uniquePlayers.size() + "\n" +
              "§7Revenue: §a$" + totals.totalRevenue.toPlainString() + "\n" +
              "§7Payouts: §c$" + totals.totalPayout.toPlainString() + "\n" +
              "§7Net: §e$" + totals.getNetProfit().toPlainString()
            ));
          }
          return 1;
        }));
  }

  private static final DateTimeFormatter TX_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    .withZone(ZoneId.systemDefault());

  private static int showTransactions(ServerCommandSource source, java.util.UUID uuid, String name,
                                      ShopOptionsApi options) {
    ShopContext ctx = ShopContext.get();
    ShopConfig config = ctx.getConfigs().get(options.getModId());
    int limit = config != null ? config.getTransactionPageSize() : 10;
    var repo = ctx.getRepositories();
    if (repo == null) {
      source.sendMessage(Text.literal("§cNo repository available"));
      return 0;
    }
    List<Transaction> transactions = repo.getTransactionRepository().findByPlayer(uuid, limit);
    if (transactions.isEmpty()) {
      source.sendMessage(Text.literal("§7No transactions found for " + name));
      return 1;
    }

    StringBuilder sb = new StringBuilder("§6--- Transactions for §e" + name + " §6---\n");
    for (Transaction tx : transactions) {
      String action = tx.getAction() == ActionShop.BUY ? "§aBUY" : "§cSELL";
      String date = TX_FORMAT.format(Instant.ofEpochMilli(tx.getTimestamp()));
      sb.append(String.format("§7[%s§7] %s §7x%d §e%s §7(%s §7%s)\n",
        date, action, tx.getAmount(), tx.getProductId(),
        tx.getValue().toPlainString(), tx.getCurrency()));
    }
    source.sendMessage(Text.literal(sb.toString()));
    return 1;
  }

  private static int openShopForPlayers(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                        ShopOptionsApi options, boolean withClose) {
    try {
      var players = EntityArgumentType.getPlayers(ctx, "player");
      String shopId = StringArgumentType.getString(ctx, "IdShop");
      ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
      Shop shop = ShopContext.get().getShops(options.getModId()).stream()
        .filter(s -> s.getId().equals(shopId))
        .findFirst().orElse(null);

      if (shop == null) {
        ctx.getSource().sendMessage(Text.literal("Shop not found: " + shopId));
        return 0;
      }

      for (ServerPlayerEntity player : players) {
        NavigationContext nav = new NavigationContext();
        nav.push(shop);
        ShopMenuBuilder.openShop(player, shop, nav, config, withClose);
      }
      return 1;
    } catch (Exception e) {
      e.printStackTrace();
      return 0;
    }
  }
}

