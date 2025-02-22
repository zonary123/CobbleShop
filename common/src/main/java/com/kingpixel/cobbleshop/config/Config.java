package com.kingpixel.cobbleshop.config;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.GooeyButton;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.ShopTypeDynamic;
import com.kingpixel.cobbleshop.adapters.ShopTypeDynamicWeekly;
import com.kingpixel.cobbleshop.adapters.ShopTypePermanent;
import com.kingpixel.cobbleshop.adapters.ShopTypeWeekly;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.models.Shop;
import com.kingpixel.cobbleshop.models.TypeShop;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.util.Utils;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:27
 */
@Data
public class Config {
  private String Path;
  // Essential fields
  private boolean debug;
  private String lang;
  private int rows;
  private String title;
  private String soundOpen;
  private String soundClose;
  private ItemModel itemClose;
  private List<String> commands;
  private List<PanelsConfig> panels;

  public Config() {
    this.debug = false;
    this.lang = "en";
    this.rows = 6;
    this.title = "Shop";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.itemClose = new ItemModel(49, "minecraft:barrier", "&cClose", List.of(), 0);
    this.commands = new ArrayList<>();
    commands.add("shop");
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public void readConfig(ShopOptionsApi options) {
    String path = options.getPath();
    File folder = Utils.getAbsolutePath(path);

    if (!folder.exists()) {
      folder.mkdirs();
    }
    CompletableFuture<Boolean> futureRead = Utils.readFileAsync(options.getPath(), "config.json", call -> {
      Config config = CobbleShop.gson.fromJson(call, Config.class);
      write(config, options);
    });

    if (!futureRead.join()) {
      Config config = this;
      write(config, options);
    }
  }

  private void write(Config config, ShopOptionsApi options) {
    config.check();
    ShopApi.configs.put(options.getModId(), config);
    CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(options.getPath(), "config.json",
      CobbleShop.gson.toJson(config));
    if (!futureWrite.join()) {
      CobbleUtils.LOGGER.error("Error writing file: " + options.getPath() + "config.json");
    }
  }

  private void check() {
    if (commands == null || commands.isEmpty()) {
      commands = new ArrayList<>();
      commands.add("shop");
    }
  }

  public static void readShops(ShopOptionsApi options) {
    ShopApi.shops.getOrDefault(options.getModId(), new ArrayList<>()).clear();
    String path = options.getPath() + "shop/";
    File folder = Utils.getAbsolutePath(path);
    if (!folder.exists()) {
      folder.mkdirs();
      createDefaultShop(path);
    }
    List<Shop> shops = new ArrayList<>();
    readAllShops(folder.listFiles(), shops);
    ShopApi.shops.put(options.getModId(), shops);
  }

  private static void createDefaultShop(String path) {
    File folder = Utils.getAbsolutePath(path);
    if (!folder.exists()) {
      folder.mkdirs();
    }
    List<Shop> shops = new ArrayList<>();
    shops.add(new Shop(TypeShop.PERMANENT.name(), new ShopTypePermanent()));
    shops.add(new Shop(TypeShop.DYNAMIC.name(), new ShopTypeDynamic()));
    shops.add(new Shop(TypeShop.WEEKLY.name(), new ShopTypeWeekly()));
    shops.add(new Shop(TypeShop.DYNAMIC_WEEKLY.name(), new ShopTypeDynamicWeekly()));
    int i = 0;
    for (Shop shop : shops) {
      shop.getDisplay().setSlot(i++);
      shop.check();
      CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync(path, shop.getId() + ".json", CobbleShop.gson.toJson(shop));
      if (!futureWrite.join()) {
        CobbleUtils.LOGGER.error("Error writing file: " + path + shop.getId() + ".json");
      }
    }
  }

  private static void readAllShops(File[] files, List<Shop> shops) {
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) {
        readAllShops(file.listFiles(), shops);
      } else {
        if (file.getName().endsWith(".json")) {
          Shop shop = null;
          try {
            shop = CobbleShop.gson.fromJson(Utils.readFileSync(file), Shop.class);
            shop.setId(file.getName().replace(".json", ""));
            shop.check();
            Utils.writeFileSync(file, CobbleShop.gson.toJson(shop));
          } catch (IOException e) {
            e.printStackTrace();
          }
          shops.add(shop);
        }
      }
    }
  }

  public void open(ServerPlayerEntity player, ShopOptionsApi options) {
    ChestTemplate template = ChestTemplate
      .builder(rows)
      .build();


    PanelsConfig.applyConfig(template, panels);

    List<Shop> shops = ShopApi.getShops(options);

    applyShops(shops, player, options, this, template);

    ItemModel close = CobbleShop.lang.getGlobalItemClose(itemClose);
    GooeyButton closeButton = close.getButton(1, action -> UIManager.closeUI(player));
    template.set(itemClose.getSlot(), closeButton);

    GooeyPage page = GooeyPage
      .builder()
      .template(template)
      .title(title)
      .build();

    UIManager.openUIForcefully(player, page);
  }

  public static void applyShops(List<Shop> shops, ServerPlayerEntity player, ShopOptionsApi options, Config config,
                                ChestTemplate template) {
    for (Shop shop : shops) {
      ItemModel display = CobbleShop.lang.getGlobalDisplay(shop.getDisplay());
      List<String> lore = new ArrayList<>(display.getLore());
      lore.replaceAll(s -> shop.getType().replace(s, shop, options));
      GooeyButton button = display.getButton(
        1,
        display.getDisplayname().replace("%shop%", shop.getId()),
        lore,
        action -> shop.open(player, options, config, 0, null)
      );
      template.set(shop.getDisplay().getSlot(), button);
    }
  }
}
