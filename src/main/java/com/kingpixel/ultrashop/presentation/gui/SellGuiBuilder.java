package com.kingpixel.ultrashop.presentation.gui;

import com.kingpixel.ultrashop.domain.service.TransactionService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens a vanilla 27-slot chest inventory for players to place items they want to sell.
 * On close, it automatically calls TransactionService.sellAll and returns unsold items.
 */
public final class SellGuiBuilder {

  private SellGuiBuilder() {
  }

  /**
   * Opens the Sell GUI for the player.
   */
  public static void open(ServerPlayerEntity player) {
    SimpleInventory tempInventory = new SimpleInventory(27) {
      @Override
      public void onClose(PlayerEntity playerEntity) {
        super.onClose(playerEntity);
        if (!(playerEntity instanceof ServerPlayerEntity sp)) return;

        List<ItemStack> itemsToSell = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
          ItemStack stack = getStack(i);
          if (!stack.isEmpty()) {
            itemsToSell.add(stack);
          }
        }

        if (!itemsToSell.isEmpty()) {
          // Pass items to TransactionService.sellAll
          TransactionService.sellAll(sp, itemsToSell);

          // Return any items that weren't fully sold back to player inventory/world
          for (ItemStack remaining : itemsToSell) {
            if (!remaining.isEmpty() && remaining.getCount() > 0) {
              if (!sp.getInventory().insertStack(remaining)) {
                sp.dropItem(remaining, false);
              }
            }
          }
        }
      }
    };

    NamedScreenHandlerFactory factory = new SimpleNamedScreenHandlerFactory(
      (syncId, playerInv, playerEntity) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, tempInventory),
      Text.literal("Sell GUI - Place items to sell")
    );

    player.openHandledScreen(factory);
  }
}
