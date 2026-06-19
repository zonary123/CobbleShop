package com.kingpixel.ultrashop.presentation.command;

import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.ShopBridge;
import com.kingpixel.ultrashop.domain.service.StatsService;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.presentation.gui.*;
import com.kingpixel.ultrashop.presentation.gui.edit.ShopEditMenuBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * Command tree registration — clean delegation to services and GUI builders.
 */
public final class CommandTree {

  private static final String ADMIN_PERMISSION_SUFFIX = ".admin";
  private static final String ARG_PLAYER = "player";
  private static final String ARG_SHOP = "shop";
  private static final String ARG_SHOP_ID = "IdShop";
  private static final String ARG_WITH_CLOSE = "WithClose";

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

    base.requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".base", modId + ADMIN_PERMISSION_SUFFIX), 2));
    base.executes(ctx -> openMainMenu(ctx.getSource(), options));
    registerReload(base, modId, options);
    registerOther(base, modId, options);
    registerCreate(base, modId, options);
    registerRestartShop(base, modId, options);
    registerTransactions(base, modId, options);
    registerDelete(base, modId, options);
    registerEdit(base, modId, options);
    registerStats(base, modId, options);
    registerMaintenance(base, modId, options);
    return base;
  }

  private static final DateTimeFormatter TX_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    .withZone(ZoneId.systemDefault());

  private static int showTransactions(ServerCommandSource source, java.util.UUID uuid, String name,
                                      ShopOptionsApi options) {
    ShopContext ctx = ShopContext.get();
    ShopConfig config = ctx.getConfigs().get(options.getModId());
    LangConfig lang = ctx.getLang();
    int limit = config != null ? config.getTransactionPageSize() : 10;
    com.kingpixel.ultrashop.infrastructure.persistence.RepositoryFactory repo = ctx.getRepositories();
    if (repo == null) {
      sendConfiguredMessage(source, lang.getCommandNoRepository());
      return 0;
    }
    List<Transaction> transactions = repo.getTransactionRepository().findByPlayer(uuid, limit);
    if (transactions.isEmpty()) {
      sendConfiguredMessage(source, lang.getCommandNoTransactionsFound().replace("%player%", name));
      return 1;
    }

    StringBuilder sb = new StringBuilder(resolveLang(lang.getCommandTransactionsHeader())
      .replace("%player%", name));
    for (Transaction tx : transactions) {
      String action = tx.getAction() == ActionShop.BUY ? lang.getTransactionBuyLabel() : lang.getTransactionSellLabel();
      String date = TX_FORMAT.format(Instant.ofEpochMilli(tx.getTimestamp()));
      sb.append(String.format("%n%s", resolveLang(lang.getCommandTransactionLine())
        .replace("%date%", date)
        .replace("%action%", action)
        .replace("%amount%", String.valueOf(tx.getAmount()))
        .replace("%product%", tx.getProductId())
        .replace("%price%", tx.getValue().toPlainString())
        .replace("%currency%", tx.getCurrency())));
    }
    source.sendMessage(AdventureTranslator.toNative(sb.toString()));
    return 1;
  }

  private static int openShopForPlayers(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                                        ShopOptionsApi options, boolean withClose) {
    try {
      Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, ARG_PLAYER);
      String shopId = StringArgumentType.getString(ctx, ARG_SHOP_ID);
      ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
      Shop shop = ShopContext.get().getTypedShops(options.getModId()).stream()
        .filter(s -> s.getId().equals(shopId))
        .findFirst().orElse(null);

      if (shop == null) {
        sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandShopNotFound().replace("%shop%", shopId));
        return 0;
      }

      for (ServerPlayerEntity player : players) {
        NavigationContext nav = new NavigationContext();
        nav.push(shop);
        ShopMenuBuilder.openShop(player, shop, nav, config, withClose);
      }
      return 1;
    } catch (Exception e) {
      UltraShop.LOGGER.error("Error opening shop for players: " + e.getMessage());
      return 0;
    }
  }



  private static void registerReload(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                     ShopOptionsApi options) {
    base.then(CommandManager.literal("reload")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".reload", modId + ADMIN_PERMISSION_SUFFIX), 2))
      .executes(ctx -> {
        try{
          ConfigLoader.load(options);
          StatsService.invalidateCache();
          ShopContext.get().startDashboard();
          sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandReloaded()
            .replace("%modId%", options.getModId()));
        } catch (Exception e) {
          UltraShop.LOGGER.error("Error reloading shops: {}", e.getMessage(), e);
          sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandReloadFailed()
            .replace("%modId%", options.getModId())
            .replace("%prefix%", ShopContext.get().getLang().getPrefix())
            .replace("%error%", e.getMessage()));
        }
        return 1;
      }));
  }

  private static void registerOther(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                    ShopOptionsApi options) {
    base.then(CommandManager.literal("other")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
      .then(CommandManager.argument(ARG_PLAYER, EntityArgumentType.players())
        .executes(ctx -> {
          ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
          for (ServerPlayerEntity player : EntityArgumentType.getPlayers(ctx, ARG_PLAYER)) {
            MainMenuBuilder.open(player, config, options.getModId());
          }
          return 1;
        })
        .then(CommandManager.argument(ARG_SHOP_ID, StringArgumentType.string())
          .suggests((ctx, builder) -> {
            ShopContext.get().getTypedShops(options.getModId()).forEach(shop -> builder.suggest(shop.getId()));
            return builder.buildFuture();
          })
          .executes(ctx -> openShopForPlayers(ctx, options, true))
          .then(CommandManager.argument(ARG_WITH_CLOSE, BoolArgumentType.bool())
            .executes(ctx -> openShopForPlayers(ctx, options, BoolArgumentType.getBool(ctx, ARG_WITH_CLOSE)))))));
  }

  private static void registerCreate(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                     ShopOptionsApi options) {
    base.then(CommandManager.literal("create")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
      .then(CommandManager.argument(ARG_SHOP, StringArgumentType.string())
        .then(CommandManager.argument("dynamic", BoolArgumentType.bool())
          .executes(ctx -> {
            String id = StringArgumentType.getString(ctx, ARG_SHOP);
            boolean exists = ShopContext.get().getTypedShops(options.getModId()).stream().anyMatch(shop -> shop.getId().equals(id));
            if (exists) {
              sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandShopAlreadyExists().replace("%shop%", id));
              return 0;
            }
            boolean dynamic = BoolArgumentType.getBool(ctx, "dynamic");
            com.kingpixel.ultrashop.domain.model.shop.Shop shop;
            if (dynamic) {
              com.kingpixel.ultrashop.domain.model.shop.RotationShop r = new com.kingpixel.ultrashop.domain.model.shop.RotationShop();
              r.setId(id);
              r.setScheduler(new com.kingpixel.ultrashop.domain.scheduler.DurationScheduler("30m"));
              r.setRotationAmount(3);
              shop = r;
            } else {
              com.kingpixel.ultrashop.domain.model.shop.NormalShop n = new com.kingpixel.ultrashop.domain.model.shop.NormalShop();
              n.setId(id);
              shop = n;
            }
            ConfigLoader.createShop(options, shop);
            sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandShopCreated().replace("%shop%", id));
            return 1;
          }))));
  }

  private static void registerRestartShop(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                          ShopOptionsApi options) {
    base.then(CommandManager.literal("restartShop")
      .requires(src -> PermissionApi.hasPermission(src, modId + ".restart.shop", 2))
      .then(CommandManager.argument(ARG_SHOP, StringArgumentType.string())
        .suggests((ctx, builder) -> {
          ShopContext.get().getTypedShops(options.getModId()).stream()
            .filter(RotationShop.class::isInstance)
            .forEach(shop -> builder.suggest(shop.getId()));
          return builder.buildFuture();
        })
        .executes(ctx -> {
          String shopId = StringArgumentType.getString(ctx, ARG_SHOP);
          Shop shop = findTypedShop(options, shopId);
          if (shop instanceof RotationShop rotationShop) {
            ShopContext.get().getDataShop().updateDynamicProducts(rotationShop, options.getModId(), true);
            sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandDynamicShopRestarted().replace("%shop%", shopId));
          } else {
            sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandDynamicShopInvalid().replace("%shop%", shopId));
          }
          return 1;
        })));
  }

  private static void registerTransactions(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                           ShopOptionsApi options) {
    base.then(CommandManager.literal("transactions")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".transactions", modId + ADMIN_PERMISSION_SUFFIX), 2))
      .executes(ctx -> openOwnTransactions(ctx.getSource(), options))
      .then(CommandManager.argument(ARG_PLAYER, EntityArgumentType.player())
        .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
        .executes(ctx -> openTargetTransactions(ctx.getSource(), EntityArgumentType.getPlayer(ctx, ARG_PLAYER), options))));
  }

  private static void registerDelete(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                     ShopOptionsApi options) {
    base.then(CommandManager.literal("delete")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
      .then(CommandManager.argument(ARG_SHOP, StringArgumentType.string())
        .suggests((ctx, builder) -> {
          ShopContext.get().getTypedShops(options.getModId()).forEach(shop -> builder.suggest(shop.getId()));
          return builder.buildFuture();
        })
        .executes(ctx -> deleteShop(ctx.getSource(), options, StringArgumentType.getString(ctx, ARG_SHOP)))));
  }

  private static void registerEdit(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                   ShopOptionsApi options) {
    base.then(CommandManager.literal("edit")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
      .executes(ctx -> {
        if (!ctx.getSource().isExecutedByPlayer()) return 0;
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
        ShopEditMenuBuilder.openShopList(player, config, options.getModId());
        return 1;
      }));
  }

  private static void registerStats(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                    ShopOptionsApi options) {
    base.then(CommandManager.literal("stats")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ".stats", modId + ADMIN_PERMISSION_SUFFIX), 2))
      .executes(ctx -> {
        if (ctx.getSource().isExecutedByPlayer()) {
          ServerPlayerEntity player = ctx.getSource().getPlayer();
          if (player == null) return 0;
          ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
          StatsMenuBuilder.open(player, config, options.getModId());
          return 1;
        }
        sendConfiguredMessage(ctx.getSource(), buildConsoleStatsMessage(StatsService.getServerTotals(30), ShopContext.get().getLang()));
        return 1;
      }));
  }

  private static int openMainMenu(ServerCommandSource source, ShopOptionsApi options) {
    if (!source.isExecutedByPlayer()) return 0;
    ServerPlayerEntity player = source.getPlayer();
    if (player == null) return 0;
    ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
    MainMenuBuilder.open(player, config, options.getModId());
    return 1;
  }

  private static int openOwnTransactions(ServerCommandSource source, ShopOptionsApi options) {
    if (!source.isExecutedByPlayer()) return 0;
    ServerPlayerEntity player = source.getPlayer();
    if (player == null) return 0;
    ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
    TransactionMenuBuilder.open(player, player.getUuid(), player.getGameProfile().getName(), config, options.getModId());
    return 1;
  }

  private static int openTargetTransactions(ServerCommandSource source, ServerPlayerEntity target,
                                            ShopOptionsApi options) {
    if (source.isExecutedByPlayer()) {
      ServerPlayerEntity viewer = source.getPlayer();
      if (viewer != null) {
        ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
        TransactionMenuBuilder.open(viewer, target.getUuid(), target.getGameProfile().getName(), config, options.getModId());
        return 1;
      }
    }
    return showTransactions(source, target.getUuid(), target.getGameProfile().getName(), options);
  }

  private static int deleteShop(ServerCommandSource source, ShopOptionsApi options, String shopId) {
    Shop typedShop = findTypedShop(options, shopId);
    if (typedShop == null) {
      sendConfiguredMessage(source, ShopContext.get().getLang().getCommandShopNotFound().replace("%shop%", shopId));
      return 0;
    }
    try {
      Path shopPath = com.kingpixel.cobbleutils.CobbleUtils.getPath()
        .resolve(options.getPath())
        .resolve("shop")
        .resolve(shopId + ".json");
      Files.deleteIfExists(shopPath);
      ShopContext.get().removeTypedShop(options.getModId(), shopId);
      ShopContext.get().getSellIndex().rebuild(ShopContext.get().getTypedShops());
      sendConfiguredMessage(source, ShopContext.get().getLang().getCommandShopDeleted().replace("%shop%", shopId));
    } catch (Exception e) {
      sendConfiguredMessage(source, ShopContext.get().getLang().getCommandShopDeleteError().replace("%error%", e.getMessage()));
    }
    return 1;
  }

  private static Shop findTypedShop(ShopOptionsApi options, String shopId) {
    return ShopContext.get().getTypedShops(options.getModId()).stream()
      .filter(shop -> shop.getId().equals(shopId))
      .findFirst()
      .orElse(null);
  }

  private static String buildConsoleStatsMessage(StatsService.ServerTotals totals, LangConfig lang) {
    return String.join(String.format("%n"),
      resolveLang(lang.getCommandStatsConsoleTitle()),
      resolveLang(lang.getCommandStatsConsoleTransactions()).replace("%value%", String.valueOf(totals.totalTransactions)),
      resolveLang(lang.getCommandStatsConsolePlayers()).replace("%value%", String.valueOf(totals.uniquePlayers.size())),
      resolveLang(lang.getCommandStatsConsoleRevenue()).replace("%value%", totals.totalRevenue.toPlainString()),
      resolveLang(lang.getCommandStatsConsolePayouts()).replace("%value%", totals.totalPayout.toPlainString()),
      resolveLang(lang.getCommandStatsConsoleNet()).replace("%value%", totals.getNetProfit().toPlainString()));
  }

  private static void registerMaintenance(LiteralArgumentBuilder<ServerCommandSource> base, String modId,
                                          ShopOptionsApi options) {
    base.then(CommandManager.literal("maintenance")
      .requires(src -> PermissionApi.hasPermission(src, List.of(modId + ADMIN_PERMISSION_SUFFIX), 2))
      .then(CommandManager.argument(ARG_SHOP, StringArgumentType.string())
        .suggests((ctx, builder) -> {
          ShopContext.get().getTypedShops(options.getModId()).forEach(shop -> builder.suggest(shop.getId()));
          return builder.buildFuture();
        })
        .then(CommandManager.argument("active", BoolArgumentType.bool())
          .executes(ctx -> {
            String shopId = StringArgumentType.getString(ctx, ARG_SHOP);
            boolean active = BoolArgumentType.getBool(ctx, "active");
            Shop shop = findTypedShop(options, shopId);
            if (shop == null) {
              sendConfiguredMessage(ctx.getSource(), ShopContext.get().getLang().getCommandShopNotFound().replace("%shop%", shopId));
              return 0;
            }
            shop.setMaintenance(active);
            
            // Re-save shop to persist state
            if (shop.getFilePath() == null) {
              shop.setFilePath(com.kingpixel.cobbleutils.CobbleUtils.getPath()
                .resolve(options.getPath()).resolve("shop").resolve(shop.getId() + ".json").toString());
            }
            ShopContext.get().replaceShop(options.getModId(), shop);
            ConfigLoader.saveShop(shop);

            // Send feedback
            String status = active ? "§cCLOSED (Maintenance)" : "§aOPEN";
            sendConfiguredMessage(ctx.getSource(), "%prefix% §7Tienda §e" + shopId + " §7ahora está " + status);

            // Send webhook to Discord
            ShopConfig config = ShopContext.get().getConfigs().get(options.getModId());
            String webhookUrl = shop.getWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank()) {
              if (config != null && config.getWebhooks() != null) {
                webhookUrl = config.getWebhooks().getMaintenanceWebhookUrl();
              }
            }
            
            if (webhookUrl != null && !webhookUrl.isBlank()) {
              String rawShopName = shop.getDisplayConfig() != null && shop.getDisplayConfig().getName() != null
                ? shop.getDisplayConfig().getName() : shopId;
              String cleanShopName = rawShopName.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", "");
              String title = "Mantenimiento de Tienda: " + shopId;
              String desc = "La tienda **" + shopId + "** (" + cleanShopName + ") ha sido **" + (active ? "CERRADA para mantenimiento" : "ABIERTA al público") + "**.";
              int color = active ? 0xFF0000 : 0x00FF00;
              String payload = com.kingpixel.ultrashop.infrastructure.webhook.DiscordWebhookHelper.buildEmbedJson(title, desc, color);
              com.kingpixel.ultrashop.infrastructure.webhook.DiscordWebhookHelper.sendWebhook(webhookUrl, payload);
            }

            return 1;
          }))));
  }

  private static void sendConfiguredMessage(ServerCommandSource source, String message) {
    source.sendMessage(AdventureTranslator.toNative(resolveLang(message)));
  }

  private static String resolveLang(String text) {
    if (text == null) return "";
    return text.replace("%prefix%", ShopContext.get().getLang().getPrefix());
  }
}

