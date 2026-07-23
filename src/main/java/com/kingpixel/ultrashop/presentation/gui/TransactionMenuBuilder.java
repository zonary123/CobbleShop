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
import com.kingpixel.ultrashop.domain.model.ActionShop;
import com.kingpixel.ultrashop.domain.model.Transaction;
import com.kingpixel.ultrashop.infrastructure.config.LangConfig;
import com.kingpixel.ultrashop.infrastructure.config.ShopConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import ca.landonjw.gooeylibs2.api.button.ButtonAction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import java.util.function.Consumer;

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
          buttons.add(buildTransactionButton(tx, lang));
        }

        ChestTemplate template = ChestTemplate.builder(6).build();

        ItemModel closeItem = lang.getGlobalItemClose();
        template.set(49, getButton(closeItem, action -> {
          if (config != null) {
            MainMenuBuilder.open(viewer, config, modId);
          } else {
            UIManager.closeUI(viewer);
          }
        }));

        ItemModel prev = lang.getGlobalItemPrevious();
        template.set(45, LinkedPageButton.builder()
          .display(prev.getItemStack()).linkType(LinkType.Previous).build());

        ItemModel next = lang.getGlobalItemNext();
        template.set(53, LinkedPageButton.builder()
          .display(next.getItemStack()).linkType(LinkType.Next).build());

        new Rectangle(0, 0, 5, 9).apply(template);

        String title = applyTemplate(lang.getTransactionMenuTitle(), targetName);
        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title));

        GooeyPage page = buttons.isEmpty()
          ? linkedPage.build()
          : PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);

        ctx.runOnServer(() -> UIManager.openUIForcefully(viewer, page));
      } catch (Exception e) {
        UltraShop.LOGGER.error("Error opening transaction menu", e);
      }
    });
  }

  private static GooeyButton buildTransactionButton(Transaction transaction, LangConfig lang) {
    boolean isBuy = transaction.getAction() == ActionShop.BUY;
    String item = isBuy ? "minecraft:lime_stained_glass_pane" : "minecraft:red_stained_glass_pane";
    String actionLabel = isBuy ? lang.getTransactionBuyLabel() : lang.getTransactionSellLabel();
    String date = DATE_FMT.format(Instant.ofEpochMilli(transaction.getTimestamp()));
    List<String> lore = List.of(
      formatValue(lang.getTransactionDateLabel(), date),
      formatValue(lang.getTransactionShopLabel(), transaction.getShopId()),
      formatValue(lang.getTransactionProductLabel(), transaction.getProductId()),
      formatValue(lang.getTransactionAmountLabel(), String.valueOf(transaction.getAmount())),
      formatValue(lang.getTransactionPriceLabel(), transaction.getValue().toPlainString() + " " + transaction.getCurrency())
    );

    return GooeyButton.builder()
      .display(new ItemModel(item).getItemStack())
      .with(DataComponentTypes.CUSTOM_NAME,
        AdventureTranslator.toNative(actionLabel + " §7- " + transaction.getProductId()))
      .with(DataComponentTypes.LORE,
        new LoreComponent(AdventureTranslator.toNativeL(lore)))
      .build();
  }

  private static String applyTemplate(String template, String value) {
    return template.replace("%player%", value);
  }

  private static String formatValue(String template, String value) {
    return template.replace("%value%", value);
  }

  private static GooeyButton getButton(ItemModel model, Consumer<ButtonAction> onClick) {
    return GooeyButton.builder()
      .display(model.getItemStack())
      .onClick(onClick::accept)
      .build();
  }
}

