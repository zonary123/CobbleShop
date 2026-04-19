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
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds and opens a paginated menu for viewing transaction history.
 */
public final class TransactionMenuBuilder {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    .withZone(ZoneId.systemDefault());

  private TransactionMenuBuilder() {
  }

  /**
   * Opens a transaction history GUI for the given player's transactions.
   */
  public static void open(ServerPlayerEntity viewer, UUID targetUuid, String targetName,
                          ShopConfig config, String modId) {
    ShopContext ctx = ShopContext.get();

    ctx.getAsyncContext().runAsync(() -> {
      try {
        LangConfig lang = ctx.getLang();
        int limit = config != null ? config.getTransactionPageSize() * 5 : 50;
        List<Transaction> transactions = ctx.getRepositories()
          .getTransactionRepository().findByPlayer(targetUuid, limit);

        List<Button> buttons = new ArrayList<>();
        for (Transaction tx : transactions) {
          boolean isBuy = tx.getAction() == ActionShop.BUY;
          String item = isBuy ? "minecraft:lime_stained_glass_pane" : "minecraft:red_stained_glass_pane";
          String actionLabel = isBuy ? "§aBUY" : "§cSELL";
          String date = DATE_FMT.format(Instant.ofEpochMilli(tx.getTimestamp()));

          List<String> lore = List.of(
            "§7Date: §f" + date,
            "§7Shop: §f" + tx.getShopId(),
            "§7Product: §f" + tx.getProductId(),
            "§7Amount: §f" + tx.getAmount(),
            "§7Price: §e" + tx.getValue().toPlainString() + " " + tx.getCurrency()
          );

          GooeyButton button = GooeyButton.builder()
            .display(new ItemModel(item).getItemStack())
            .with(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
              AdventureTranslator.toNative(actionLabel + " §7- " + tx.getProductId()))
            .with(net.minecraft.component.DataComponentTypes.LORE,
              new net.minecraft.component.type.LoreComponent(AdventureTranslator.toNativeL(lore)))
            .build();

          buttons.add(button);
        }

        ChestTemplate template = ChestTemplate.builder(6).build();

        // Close button
        ItemModel closeItem = lang.getGlobalItemClose();
        template.set(49, closeItem.getButton(1, action -> {
          if (config != null) {
            MainMenuBuilder.open(viewer, config, modId);
          } else {
            UIManager.closeUI(viewer);
          }
        }));

        // Pagination
        ItemModel prev = lang.getGlobalItemPrevious();
        template.set(45, LinkedPageButton.builder()
          .display(prev.getItemStack()).linkType(LinkType.Previous).build());

        ItemModel next = lang.getGlobalItemNext();
        template.set(53, LinkedPageButton.builder()
          .display(next.getItemStack()).linkType(LinkType.Next).build());

        new Rectangle(0, 0, 5, 9).apply(template);

        String title = lang.getPrefix() + " Transactions: " + targetName;
        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title));

        GooeyPage page = buttons.isEmpty()
          ? linkedPage.build()
          : PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);

        ctx.runOnServer(() -> UIManager.openUIForcefully(viewer, page));
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }
}

