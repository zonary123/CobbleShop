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
            AdventureTranslator.toNative("§6⚡ Server Overview §7(30d)"))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(List.of(
            "§8─────────────────────",
            "§7Total Transactions: §f" + totals.totalTransactions,
            "§7Unique Players: §f" + totals.uniquePlayers.size(),
            "§8─────────────────────",
            "§7Revenue (buys): §a$" + totals.totalRevenue.toPlainString(),
            "§7Payouts (sells): §c$" + totals.totalPayout.toPlainString(),
            "§7Net Profit: §e$" + totals.getNetProfit().toPlainString(),
            "§8─────────────────────"
          ))))
          .build());

        // --- Player stats (slot 0 - player head) ---
        StatsService.PlayerAggregate playerStats = StatsService.getPlayerStats(player.getUuid(), MAX_DAYS);
        template.set(0, GooeyButton.builder()
          .display(new ItemModel("minecraft:player_head").getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME,
            AdventureTranslator.toNative("§b👤 Your Stats §7(30d)"))
          .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(List.of(
            "§8─────────────────────",
            "§7Items Bought: §f" + playerStats.totalBought,
            "§7Items Sold: §f" + playerStats.totalSold,
            "§7Total Spent: §c$" + playerStats.totalSpent.toPlainString(),
            "§7Total Earned: §a$" + playerStats.totalEarned.toPlainString(),
            "§8─────────────────────"
          ))))
          .build());

        // --- Shop breakdown (slot 8 - right) ---
        Map<String, StatsService.ShopAggregate> shopStats = StatsService.getShopStats(MAX_DAYS);
        List<String> shopLore = new ArrayList<>();
        shopLore.add("§8─────────────────────");
        for (var entry : shopStats.entrySet()) {
          var s = entry.getValue();
          shopLore.add("§e" + entry.getKey() + "§7: §a$" + s.revenue.toPlainString()
            + " §7/ §c$" + s.payout.toPlainString()
            + " §7(" + s.totalTransactions + " tx)");
        }
        if (shopLore.size() == 1) shopLore.add("§7No data yet");
        shopLore.add("§8─────────────────────");

        template.set(8, GooeyButton.builder()
          .display(new ItemModel("minecraft:chest").getItemStack())
          .with(DataComponentTypes.CUSTOM_NAME,
            AdventureTranslator.toNative("§6🏪 Shop Breakdown §7(30d)"))
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
            "§7Shop: §f" + ps.getShopId(),
            "§7Rank: §e#" + rank,
            "§8─────────────────────",
            "§7Bought: §f" + ps.getTotalBought() + "x §7by §f" + ps.getUniqueBuyers().size() + " players",
            "§7Sold: §f" + ps.getTotalSold() + "x §7by §f" + ps.getUniqueSellers().size() + " players",
            "§8─────────────────────",
            "§7Revenue: §a$" + ps.getTotalRevenue().toPlainString(),
            "§7Payouts: §c$" + ps.getTotalPayout().toPlainString(),
            "§7Net: §e$" + ps.getNetProfit().toPlainString(),
            "§8─────────────────────",
            "§7Unique Players: §f" + ps.getUniquePlayers()
          );

          buttons.add(GooeyButton.builder()
            .display(new ItemModel(item).getItemStack())
            .with(DataComponentTypes.CUSTOM_NAME,
              AdventureTranslator.toNative("§e#" + rank + " §f" + ps.getProductId()))
            .with(DataComponentTypes.LORE, new LoreComponent(AdventureTranslator.toNativeL(lore)))
            .build());
        }

        // Close button
        ItemModel closeItem = lang.getGlobalItemClose();
        template.set(49, closeItem.getButton(1, action -> UIManager.closeUI(player)));

        // Pagination
        ItemModel prev = lang.getGlobalItemPrevious();
        template.set(45, LinkedPageButton.builder()
          .display(prev.getItemStack()).linkType(LinkType.Previous).build());
        ItemModel next = lang.getGlobalItemNext();
        template.set(53, LinkedPageButton.builder()
          .display(next.getItemStack()).linkType(LinkType.Next).build());

        new Rectangle(1, 0, 4, 9).apply(template);

        String title = "§6UltraShop Stats §7(30 days)";
        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title));

        GooeyPage page = buttons.isEmpty()
          ? linkedPage.build()
          : PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);

        ctx.runOnServer(() -> UIManager.openUIForcefully(player, page));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }
}

