package com.kingpixel.ultramarry;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kingpixel.ultramarry.command.CommandTree;
import com.kingpixel.ultramarry.config.Config;
import com.kingpixel.ultramarry.config.Lang;
import com.kingpixel.ultramarry.database.DataBaseClient;
import com.kingpixel.ultramarry.database.DataBaseFactory;
import com.kingpixel.ultramarry.models.UserInfo;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:05
 */
public class UltraMarry implements ModInitializer {

  public static final String MOD_ID = "ultramarry";
  public static final String MOD_NAME = "UltraMarry";
  public static final String PATH = "/config/ultramarry/";
  public static final String PATH_LANG = PATH + "lang/";
  public static final String PATH_DATA = PATH + "data";
  public static MinecraftServer server;
  public static Config config = new Config();
  public static Lang lang = new Lang();
  public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
    .setDaemon(true)
    .setNameFormat("ultramarry-%d")
    .build());

  @Override public void onInitialize() {
    events();
  }

  public static void load() {
    lang.init();
    config.init();
    new DataBaseFactory(config.getDatabase());
  }

  public static void events() {
    load();
    LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> server = level.getServer());

    CommandRegistrationEvent.EVENT.register(CommandTree::register);

    PlayerEvent.PLAYER_JOIN.register(player -> runAsync(() -> DataBaseFactory.INSTANCE.getUserInfo(player.getUuid())));

    PlayerEvent.PLAYER_QUIT.register(player -> DataBaseClient.CACHE.invalidate(player.getUuid()));

    try {
      Placeholders.register(Identifier.of("ultramarry:gender"), (context, string) -> {
        UserInfo userInfo = getUserInfo(context);
        if (userInfo == null) return PlaceholderResult.invalid();
        return PlaceholderResult.value(userInfo.obtainGender());
      });
      Placeholders.register(Identifier.of("ultramarry:marry"), (context, string) -> {
        UserInfo userInfo = getUserInfo(context);
        if (userInfo == null) return PlaceholderResult.invalid();
        return PlaceholderResult.value(userInfo.obtainMarry());
      });
    } catch (NoClassDefFoundError | Exception ignored) {

    }
  }

  private static UserInfo getUserInfo(PlaceholderContext context) {
    if (!context.hasPlayer()) return null;
    ServerPlayerEntity player = context.player();
    if (player == null) return null;
    return DataBaseFactory.INSTANCE.getUserInfo(player.getUuid());
  }

  public static void runAsync(Runnable runnable) {
    CompletableFuture.runAsync(runnable, EXECUTOR)
      .orTimeout(5, TimeUnit.SECONDS)
      .exceptionally(e -> {
        e.printStackTrace();
        return null;
      });
  }

}
