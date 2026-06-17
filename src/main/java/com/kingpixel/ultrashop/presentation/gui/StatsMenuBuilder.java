package com.kingpixel.ultrashop.presentation.gui;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.ultrashop.ShopContext;
import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.ProductStats;
import com.kingpixel.ultrashop.domain.service.StatsService;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and opens a statistics GUI showing top products, shop revenue, and player stats.
 */
public final class StatsMenuBuilder {

  private static final int MAX_DAYS = 30;

  private StatsMenuBuilder() {
  }

  /**
   * Opens the main stats overview.
   */
  public static void open(ServerPlayerEntity player, ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();

    ctx.getAsyncContext().runAsync(() -> {
      try {
        LangConfig lang = ctx.getLang();
        ChestTemplate template = ChestTemplate.builder(6).build();

        // --- Server totals (slot 4 - top center) ---
        StatsService.ServerTotals totals = StatsService.getServerTotals(MAX_DAYS);
        template.set(4, GooeyButton.builder()
          .display(new ItemModel("minecraft:nether_star").getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME,
            AdventureTranslator.toNative(lang.getStatsServerOverviewTitle()))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(List.of(
            "§8─────────────────────",
            formatValue(lang.getStatsTotalTransactionsLabel(), String.valueOf(totals.totalTransactions)),
            formatValue(lang.getStatsUniquePlayersLabel(), String.valueOf(totals.uniquePlayers.size())),
            "§8─────────────────────",
            formatValue(lang.getStatsRevenueLabel(), totals.totalRevenue.toPlainString()),
            formatValue(lang.getStatsPayoutLabel(), totals.totalPayout.toPlainString()),
            formatValue(lang.getStatsNetProfitLabel(), totals.getNetProfit().toPlainString()),
            "§8─────────────────────"
          ))))
          .build());

        // --- Player stats (slot 0 - player head) ---
        StatsService.PlayerAggregate playerStats = StatsService.getPlayerStats(player.getUuid(), MAX_DAYS);
        template.set(0, GooeyButton.builder()
          .display(new ItemModel("minecraft:player_head").getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME,
            AdventureTranslator.toNative(lang.getStatsPlayerOverviewTitle()))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(List.of(
            "§8─────────────────────",
            formatValue(lang.getStatsItemsBoughtLabel(), String.valueOf(playerStats.totalBought)),
            formatValue(lang.getStatsItemsSoldLabel(), String.valueOf(playerStats.totalSold)),
            formatValue(lang.getStatsTotalSpentLabel(), playerStats.totalSpent.toPlainString()),
            formatValue(lang.getStatsTotalEarnedLabel(), playerStats.totalEarned.toPlainString()),
            "§8─────────────────────"
          ))))
          .build());

        // --- Shop breakdown (slot 8 - right) ---
        Map<String, StatsService.ShopAggregate> shopStats = StatsService.getShopStats(MAX_DAYS);
        List<String> shopLore = new ArrayList<>();
        shopLore.add("§8─────────────────────");
        for (Map.Entry<String, StatsService.ShopAggregate> entry : shopStats.entrySet()) {
          StatsService.ShopAggregate aggregate = entry.getValue();
          shopLore.add(lang.getStatsShopBreakdownEntry()
            .replace("%shop%", entry.getKey())
            .replace("%revenue%", aggregate.revenue.toPlainString())
            .replace("%payout%", aggregate.payout.toPlainString())
            .replace("%transactions%", String.valueOf(aggregate.totalTransactions)));
        }
        if (shopLore.size() == 1) shopLore.add(lang.getStatsNoData());
        shopLore.add("§8─────────────────────");

        template.set(8, GooeyButton.builder()
          .display(new ItemModel("minecraft:chest").getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME,
            AdventureTranslator.toNative(lang.getStatsShopBreakdownTitle()))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(shopLore)))
          .build());

        // --- Top products (paginated area) ---
        List<ProductStats> topProducts = StatsService.getProductStats(MAX_DAYS);
        List<Button> buttons = new ArrayList<>();

        for (int i = 0; i < topProducts.size(); i++) {
          ProductStats ps = topProducts.get(i);
          int rank = i + 1;
          boolean buyHeavy = ps.getTotalRevenue().compareTo(ps.getTotalPayout()) >= 0;
          String item = buyHeavy ? "minecraft:gold_ingot" : "minecraft:redstone";

          List<String> lore = List.of(
            "§8─────────────────────",
            formatValue(lang.getStatsTopProductShopLabel(), ps.getShopId()),
            formatValue(lang.getStatsTopProductRankLabel(), String.valueOf(rank)),
            "§8─────────────────────",
            lang.getStatsTopProductBoughtLabel()
              .replace("%amount%", String.valueOf(ps.getTotalBought()))
              .replace("%players%", String.valueOf(ps.getUniqueBuyers().size())),
            lang.getStatsTopProductSoldLabel()
              .replace("%amount%", String.valueOf(ps.getTotalSold()))
              .replace("%players%", String.valueOf(ps.getUniqueSellers().size())),
            "§8─────────────────────",
            formatValue(lang.getStatsRevenueLabel(), ps.getTotalRevenue().toPlainString()),
            formatValue(lang.getStatsPayoutLabel(), ps.getTotalPayout().toPlainString()),
            formatValue(lang.getStatsNetProfitLabel(), ps.getNetProfit().toPlainString()),
            "§8─────────────────────",
            formatValue(lang.getStatsTopProductUniquePlayersLabel(), String.valueOf(ps.getUniquePlayers()))
          );

          buttons.add(GooeyButton.builder()
            .display(new ItemModel(item).getItemStack())
            .with(DataComponentTypes.CUSTOM_NAME,
              AdventureTranslator.toNative(lang.getStatsTopProductTitle()
                .replace("%rank%", String.valueOf(rank))
                .replace("%product%", ps.getProductId())))
            .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
            .build());
        }

        // Close button
        ItemModel closeItem = lang.getGlobalItemClose();
        template.set(49, getButton(closeItem, action -> UIManager.closeUI(player)));

        // Pagination
        ItemModel prev = lang.getGlobalItemPrevious();
        template.set(45, LinkedPageButton.builder()
          .display(prev.getItemStack()).linkType(LinkType.Previous).build());
        ItemModel next = lang.getGlobalItemNext();
        template.set(53, LinkedPageButton.builder()
          .display(next.getItemStack()).linkType(LinkType.Next).build());

        new Rectangle(1, 0, 4, 9).apply(template);

        String title = lang.getStatsMenuTitle();
        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title));

        GooeyPage page = buttons.isEmpty()
          ? linkedPage.build()
          : PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error opening stats menu: " + e.getMessage());
      }
    });
  }

  private static String formatValue(String template, String value) {
    return template.replace("%value%", value);
  }

  private static GooeyButton getButton(ItemModel model, java.util.function.Consumer<ca.landonjw.gooeylibs2.api.button.ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(model.getItemStack())
      .onClick(onClick::accept)
      .build();
  }
}

