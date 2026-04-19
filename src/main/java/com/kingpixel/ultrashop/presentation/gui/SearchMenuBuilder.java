package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.Model.conditions.util.ConditionUtils;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.Shop;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and opens a paginated menu for displaying search results across all shops.
 */
public final class SearchMenuBuilder {

  private SearchMenuBuilder() {
  }

  public static void open(ServerPlayerEntity player, String query, String modId) {
    ShopContext ctx = ShopContext.get();
    ShopConfig config = ctx.getConfigs().get(modId);
    if (config == null) return;

    ctx.getAsyncContext().runAsync(() -> {
      try {
        LangConfig lang = ctx.getLang();
        List<Button> buttons = new ArrayList<>();
        String queryLower = query.toLowerCase();

        // Collect matching products from all shops
        List<Shop> shops = ctx.getShops(modId);
        for (Shop shop : shops) {
          // Check permissions
          if (!PermissionApi.hasPermission(player, shop.getPermission(modId), 4)) {
            continue;
          }
          // Check open conditions
          if (!shop.getOpenConditions().isEmpty() && !ConditionUtils.check(shop.getOpenConditions(), player)) {
            continue;
          }

          // Fetch active products (dynamically resolves rotational shops)
          List<Product> activeProducts;
          if (shop.getRotationSchedule() != null) {
            activeProducts = ctx.getDataShop().updateDynamicProducts(shop, modId, false);
          } else {
            activeProducts = shop.getProducts();
          }

          for (Product product : activeProducts) {
            if (product.hasErrors()) continue;

            // Match against the resolved display name only (same logic as SearchCommand suggestions)
            String matchName = resolveSearchName(product);
            boolean matches = matchName != null && matchName.toLowerCase().contains(queryLower);

            if (matches) {
              NavigationContext searchNav = new NavigationContext();
              searchNav.push(shop);

              String playerBalance = PlaceholderReplacer.buildBalanceString(product, shop, player);

              buttons.add(ProductRenderer.createButton(product, player, shop, null, 1, config, searchNav, true, playerBalance));
            }
          }
        }

        if (buttons.isEmpty()) {
          PlayerUtils.sendMessage(player,
            lang.getPrefix() + " No products found matching: " + query,
            lang.getPrefix(), TypeMessage.CHAT);
          return;
        }

        // Build generic 6-row template
        ChestTemplate template = ChestTemplate.builder(6).build();

        // Close button
        ItemModel closeItem = lang.getGlobalItemClose();
        template.set(49, closeItem.getButton(1, action -> MainMenuBuilder.open(player, config, modId)));

        // Pagination buttons
        ItemModel prev = lang.getGlobalItemPrevious();
        template.set(45, LinkedPageButton.builder()
          .display(prev.getItemStack()).linkType(LinkType.Previous).build());

        ItemModel next = lang.getGlobalItemNext();
        template.set(53, LinkedPageButton.builder()
          .display(next.getItemStack()).linkType(LinkType.Next).build());

        // Fill slots 0 to 44
        com.kingpixel.cobbleutils.Model.Rectangle bounds = new com.kingpixel.cobbleutils.Model.Rectangle(0, 0, 5, 9);
        bounds.apply(template);

        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(lang.getPrefix() + " Search: " + query));

        GooeyPage page = PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));

      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  /**
   * Resolves the name used for search matching — must be consistent with
   * {@code SearchCommand.resolveItemName()} so suggestions match results.
   */
  private static String resolveSearchName(Product product) {
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

    // Commands — match by displayname only
    if (id.startsWith("command:")) {
      return product.getDisplayname();
    }

    // Regular items — translated ItemStack name
    try {
      ItemStack stack = new ItemChance(id, 0).getItemStack();
      if (stack != null && !stack.isEmpty()) {
        String name = stack.getName().getString();
        if (name != null && !name.isBlank() && !name.startsWith("<lang:")) {
          return name;
        }
      }
    } catch (Exception ignored) {
      // fallback
    }

    // Last resort: displayname
    return product.getDisplayname();
  }
}
