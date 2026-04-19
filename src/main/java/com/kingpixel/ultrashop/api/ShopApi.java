package com.kingpixel.ultrashop.api;

import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.domain.service.TransactionService;
import com.kingpixel.ultrashop.infrastructure.config.ConfigLoader;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import com.kingpixel.ultrashop.migrate.V1ToV2Migrator;
import com.kingpixel.ultrashop.presentation.command.CommandTree;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.List;

/**
 * Public API for UltraShop — facade for other mods to register shops.
 *
 * <p>This is the ONLY entry point other mods should use.</p>
 */
public final class ShopApi {

  private ShopApi() {
  }

  /**
   * Register a shop system for a mod. Called during command registration.
   */
  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    // Run migrations before loading
    Path shopDir = com.kingpixel.cobbleutils.CobbleUtils.getPath().resolve(options.getPath()).resolve("shop");
    V1ToV2Migrator.migrateIfNeeded(shopDir);

    // Load everything
    ConfigLoader.load(options);

    // Start web dashboard if enabled
    ShopContext.get().startDashboard();

    // Register commands
    CommandTree.register(options, dispatcher);
  }

  /**
   * Reload shops for a specific mod.
   */
  public static void reload(ShopOptionsApi options) {
    ConfigLoader.load(options);
  }

  /**
   * Get the config for a specific mod.
   */
  public static ShopConfig getConfig(String modId) {
    return ShopContext.get().getConfigs().get(modId);
  }

  /**
   * Get the main UltraShop config.
   */
  public static ShopConfig getMainConfig() {
    return ShopContext.get().getMainConfig();
  }

  /**
   * Get all shops for a mod.
   */
  public static List<Shop> getShops(String modId) {
    return ShopContext.get().getShops(modId);
  }

  /**
   * Find a shop by id.
   */
  public static Shop getShop(String modId, String shopId) {
    return ShopContext.get().getShops(modId).stream()
      .filter(s -> s.getId().equals(shopId))
      .findFirst().orElse(null);
  }

  /**
   * Sell all matching items from a player's inventory.
   */
  public static void sellAll(ServerPlayerEntity player, List<ItemStack> itemStacks) {
    TransactionService.sellAll(player, itemStacks);
  }
}
