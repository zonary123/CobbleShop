package com.kingpixel.ultrashop.presentation.command;

import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.conditions.util.ConditionUtils;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.api.ShopOptionsApi;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.presentation.gui.SearchMenuBuilder;
import com.kingpixel.ultrashop.presentation.gui.ShopProducts;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /shop search <query>} — searches products across all shops.
 * Suggestions show item display names (e.g. "Dirt", "Poké Ball", "Charizard").
 */
public final class SearchCommand {

  private SearchCommand() {
  }

  public static void register(ShopOptionsApi options, CommandDispatcher<ServerCommandSource> dispatcher) {
    SuggestionProvider<ServerCommandSource> suggestions = (ctx, builder) -> {
      String input = builder.getRemaining().toLowerCase();
      ServerPlayerEntity player = ctx.getSource().isExecutedByPlayer() ? ctx.getSource().getPlayer() : null;
      Set<String> names = collectItemNames(options.getModId(), player);
      for (String name : names) {
        if (name.toLowerCase().contains(input)) {
          builder.suggest(name);
        }
      }
      return builder.buildFuture();
    };

    for (String command : options.getCommands()) {
      dispatcher.register(
        CommandManager.literal(command)
          .then(CommandManager.literal("search")
            .requires(src -> PermissionApi.hasPermission(src, "ultrashop.search.base", 4))
            .then(CommandManager.argument("query", StringArgumentType.greedyString())
              .suggests(suggestions)
              .executes(ctx -> {
                if (!ctx.getSource().isExecutedByPlayer()) return 0;
                ServerPlayerEntity player = ctx.getSource().getPlayer();
                if (player == null) return 0;

                String query = StringArgumentType.getString(ctx, "query");
                SearchMenuBuilder.open(player, query, options.getModId());
                return 1;
              })
            )
          )
      );
    }
  }

  /**
   * Collects unique item names from all shop products.
   * For items: uses {@link ItemChance#getTitle()} (e.g. "Dirt", "Poké Ball").
   * For pokemon: extracts the pokemon name (e.g. "Charizard").
   * For commands: skips (not searchable by item name).
   */
  private static Set<String> collectItemNames(String modId, ServerPlayerEntity player) {
    Set<String> names = new LinkedHashSet<>();
    List<Shop> shops = ShopContext.get().getTypedShops(modId);
    for (Shop shop : shops) {
      // Skip shops the player can't access
      if (player != null) {
        if (!PermissionApi.hasPermission(player, shop.getPermission(modId), 4)) continue;
        ConditionsConfig conditionsCfg = shop.getConditionsConfig();
        var openConditions = conditionsCfg != null ? conditionsCfg.getOpenConditions() : null;
        if (openConditions != null && !openConditions.isEmpty()
            && !ConditionUtils.check(openConditions, player)) continue;
      }
      for (Product product : ShopProducts.allConfiguredProducts(shop)) {
        // Skip products with visibility conditions the player doesn't meet
        if (player != null && product.getVisibilityConditions() != null
          && !product.getVisibilityConditions().isEmpty()
          && !ConditionUtils.check(product.getVisibilityConditions(), player)) {
          continue;
        }
        String name = resolveItemName(product);
        if (name != null && !name.isBlank()) {
          names.add(name);
        }
      }
    }
    return names;
  }

  /**
   * Resolves the item name for suggestions:
   * <ul>
   *   <li>Pokemon: extracts species name and capitalizes (e.g. "pokemon:charizard lvl:50" → "Charizard")</li>
   *   <li>Commands: returns {@code null} (not useful for search)</li>
   *   <li>Items: returns {@link ItemChance#getTitle()} (translated item name)</li>
   * </ul>
   */
  private static String resolveItemName(Product product) {
    String id = product.getProduct();

    // Pokemon — extract species name
    if (id.startsWith("pokemon:")) {
      String rest = id.substring("pokemon:".length()).trim();
      String species = rest.split("\\s+")[0];
      if (!species.isEmpty()) {
        return Character.toUpperCase(species.charAt(0)) + species.substring(1).toLowerCase();
      }
      return null;
    }

    // Commands — not searchable by item name
    if (id.startsWith("command:")) {
      return null;
    }

    // Regular items — get the translated name from the ItemStack
    try {
      ItemStack stack = new ItemChance(id, 0).getItemStack();
      if (stack != null && !stack.isEmpty()) {
        String name = stack.getName().getString();
        if (name != null && !name.isBlank() && !name.startsWith("<lang:")) {
          return name;
        }
      }
    } catch (Exception ignored) {
    }

    return null;
  }
}
