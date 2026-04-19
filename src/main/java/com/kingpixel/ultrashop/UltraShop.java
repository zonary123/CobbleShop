package com.kingpixel.ultrashop;

import org.apache.logging.log4j.Logger;

import com.kingpixel.cobbleutils.util.UtilsLogger;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.service.TransactionService;
import com.kingpixel.ultrashop.infrastructure.persistence.json.JsonUserRepository;
import com.kingpixel.ultrashop.infrastructure.persistence.mongodb.MongoUserRepository;
import com.kingpixel.ultrashop.presentation.gui.edit.ChatInputManager;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

/**
 * UltraShop v2 — Minimal bootstrap.
 * All state lives in {@link ShopContext}, async in UtilsAsync, I/O in UtilsFile.
 *
 * @author Carlos Varas Alonso
 */
public class UltraShop implements ModInitializer {

  public static final String MOD_ID = "ultrashop";
  public static final String MOD_NAME = "UltraShop";
  public static final String PATH = "ultrashop/";
  public static final Logger LOGGER = UtilsLogger.getLogger(MOD_ID);

  @Override
  public void onInitialize() {

    // Initialize context (async, data structures)
    ShopContext.get().init();

    // Register events
    registerEvents();
  }

  private void registerEvents() {
    ShopOptionsApi defaultOptions = ShopOptionsApi.builder()
      .modId(MOD_ID)
      .path(PATH)
      .build();

    // Server loaded — optionally setup server-specific config
    LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
    });

    // Server stopping — save data, shutdown async
    LifecycleEvent.SERVER_STOPPING.register(event -> {
      ShopContext.get().getDataShop().write();
      ShopContext.get().shutdown();
    });

    // Command registration — load config + register commands
    CommandRegistrationEvent.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
      com.kingpixel.ultrashop.api.ShopApi.register(defaultOptions, dispatcher);
    });

    // Player join — load user data async
    PlayerEvent.PLAYER_JOIN.register(player -> {
      ShopContext.get().getAsyncContext().runAsync(() -> {
        var repo = ShopContext.get().getRepositories();
        if (repo != null) {
          if (repo.getUserRepository() instanceof JsonUserRepository jsonRepo) {
            jsonRepo.findByPlayer(player);
          } else if (repo.getUserRepository() instanceof MongoUserRepository mongoRepo) {
            mongoRepo.findByPlayer(player);
          }
        }
      });
    });

    // Player quit — cleanup locks and cache
    PlayerEvent.PLAYER_QUIT.register(player -> {
      TransactionService.removeSellLock(player.getUuid());
      ChatInputManager.clear(player.getUuid());
      var repo = ShopContext.get().getRepositories();
      if (repo != null) {
        repo.getUserRepository().remove(player.getUuid());
      }
    });

    // Chat input — intercept messages for admin edit GUI
    ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
      if (ChatInputManager.hasPending(sender.getUuid())) {
        return !ChatInputManager.handleChat(sender, message.getContent().getString());
      }
      return true;
    });
  }
}
