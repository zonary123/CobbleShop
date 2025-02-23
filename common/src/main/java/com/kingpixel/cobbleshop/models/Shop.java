package com.kingpixel.cobbleshop.models;

import ca.landonjw.gooeylibs2.api.UIManager;
import ca.landonjw.gooeylibs2.api.button.Button;
import ca.landonjw.gooeylibs2.api.button.linked.LinkType;
import ca.landonjw.gooeylibs2.api.button.linked.LinkedPageButton;
import ca.landonjw.gooeylibs2.api.helpers.PaginationHelper;
import ca.landonjw.gooeylibs2.api.page.GooeyPage;
import ca.landonjw.gooeylibs2.api.page.LinkedPage;
import ca.landonjw.gooeylibs2.api.template.slot.TemplateSlotDelegate;
import ca.landonjw.gooeylibs2.api.template.types.ChestTemplate;
import com.kingpixel.cobbleshop.CobbleShop;
import com.kingpixel.cobbleshop.adapters.ShopType;
import com.kingpixel.cobbleshop.adapters.ShopTypePermanent;
import com.kingpixel.cobbleshop.api.ShopApi;
import com.kingpixel.cobbleshop.api.ShopOptionsApi;
import com.kingpixel.cobbleshop.config.Config;
import com.kingpixel.cobbleutils.CobbleUtils;
import com.kingpixel.cobbleutils.Model.ItemChance;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.api.PermissionApi;
import com.kingpixel.cobbleutils.util.AdventureTranslator;
import com.kingpixel.cobbleutils.util.PlayerUtils;
import com.kingpixel.cobbleutils.util.TypeMessage;
import com.kingpixel.cobbleutils.util.UIUtils;
import lombok.Data;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Carlos Varas Alonso - 21/02/2025 5:19
 */
@Data
public class Shop {
  private String Path;
  // Essential fields
  private boolean autoPlace;
  private String id;
  private String title;
  private String currency;
  private String closeCommand;
  private String soundOpen;
  private String soundClose;
  private int rows;
  private int globalDiscount;
  private ShopType type;
  private Rectangle rectangle;
  private ItemModel display;
  private ItemModel itemInfoShop;
  private ItemModel itemBalance;
  private List<SubShop> subShops;
  private List<Product> products;
  private ItemModel itemPrevious;
  private ItemModel itemClose;
  private ItemModel itemNext;
  private List<PanelsConfig> panels;

  public Shop() {
    this.autoPlace = false;
    this.id = "shop";
    this.title = "%shop%";
    this.currency = "impactor:dollars";
    this.closeCommand = "close";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = new ShopTypePermanent();
    this.rectangle = new Rectangle(1, 1, 4, 7);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    itemInfoShop.setSlot(3);
    this.itemBalance = new ItemModel("");
    itemBalance.setSlot(5);
    this.products = getDefaultProducts();
    this.itemPrevious = new ItemModel("");
    itemPrevious.setSlot(45);
    this.itemClose = new ItemModel("");
    itemClose.setSlot(49);
    this.itemNext = new ItemModel("");
    itemNext.setSlot(53);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  public Shop(String id, ShopType type) {
    this.id = id;
    this.title = "%shop%";
    this.currency = "impactor:dollars";
    this.closeCommand = "close";
    this.soundOpen = "block.chest.open";
    this.soundClose = "block.chest.close";
    this.rows = 6;
    this.globalDiscount = 0;
    this.type = type;
    this.rectangle = new Rectangle(1, 1, 4, 7);
    this.display = new ItemModel("");
    this.itemInfoShop = new ItemModel("");
    itemInfoShop.setSlot(3);
    this.itemBalance = new ItemModel("");
    itemBalance.setSlot(5);
    this.products = getDefaultProducts();
    this.itemPrevious = new ItemModel("");
    itemPrevious.setSlot(45);
    this.itemClose = new ItemModel("");
    itemClose.setSlot(49);
    this.itemNext = new ItemModel("");
    itemNext.setSlot(53);
    this.panels = List.of(
      new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), rows)
    );
  }

  private Product from(ItemChance itemChance) {
    Product product = new Product();
    product.setProduct(itemChance.getItem());
    product.setDisplay(itemChance.getDisplay());
    return product;
  }

  private List<Product> getDefaultProducts() {
    List<ItemChance> itemChances = ItemChance.defaultItemChances();
    List<Product> products = new ArrayList<>();
    for (ItemChance itemChance : itemChances) {
      products.add(from(itemChance));
    }
    products.add(new Product(true));
    return products;
  }

  public void check() {
    if (subShops == null) {
      subShops = new ArrayList<>();
      subShops.add(new SubShop(2, "PERMANENT"));
    }
  }

  public String getPermission(ShopOptionsApi options) {
    return options.getModId() + ".shop.shops." + id;
  }

  public void open(ServerPlayerEntity player, ShopOptionsApi options, Config config, int position, Shop shop) {
    try {
      if (!type.isOpen()) {
        PlayerUtils.sendMessage(
          player,
          CobbleShop.lang.getMessageShopNotOpen()
            .replace("%shop%", title),
          CobbleShop.lang.getPrefix(),
          TypeMessage.CHAT
        );
        return;
      }
      if (!PermissionApi.hasPermission(player, getPermission(options), 4)) {
        PlayerUtils.sendMessage(
          player,
          CobbleShop.lang.getMessageNotHavePermission()
            .replace("%shop%", title)
            .replace("%permission%", getPermission(options)),
          CobbleShop.lang.getPrefix(),
          TypeMessage.CHAT
        );
        return;
      }
      ChestTemplate template = ChestTemplate
        .builder(rows)
        .build();

      PanelsConfig.applyConfig(template, this.getPanels());

      int totalSlots = rectangle.getLength() * rectangle.getWidth();
      List<Product> products = type.getProducts(this, options);
      int totalProducts = products.size();
      int totalSubShops = subShops.size();
      boolean hasEnoughtButtons = subShops.isEmpty() ? totalProducts > totalSlots : totalSubShops > totalSlots;
      List<Button> buttons = new ArrayList<>();
      if (subShops.isEmpty()) {
        // Products
        if (hasEnoughtButtons || autoPlace) {
          for (Product product : products) {
            if (!product.hasErrors()) buttons.add(product.getIcon(this, null, 1, options, config));
          }
        } else {
          for (Product product : products) {
            Integer slot = product.getSlot();
            if (slot == null) {
              CobbleUtils.LOGGER.error(options.getModId(), "Slot is null -> " + product.getProduct());
              continue;
            }
            TemplateSlotDelegate templateSlotDelegate = template.getSlot(slot);
            if (templateSlotDelegate != null) {
              if (UIUtils.isInside(slot, rows)) template.set(slot, product.getIcon(this, null, 1, options, config));
            } else {
              CobbleUtils.LOGGER.error(options.getModId(),
                "Slot has a product or button -> " + slot + " Product -> " + product.getProduct());
            }
          }
        }
      } else {
        // Categories
        List<Shop> categorys = ShopApi.getShops(subShops);
        for (Shop category : categorys) {
          ItemModel display = CobbleShop.lang.getGlobalDisplay(category.getDisplay());
          Button button = display.getButton(
            1,
            display.getDisplayname().replace("%shop%", category.getId()),
            action -> category.open(player, options, config, 0, shop)
          );
          if (UIUtils.isInside(category.getDisplay().getSlot(), rows))
            template.set(category.getDisplay().getSlot(), button);
        }
      }

      if (UIUtils.isInside(itemInfoShop.getSlot(), rows)) {
        template.set(itemInfoShop.getSlot(), CobbleShop.lang.getInfoShopType().getShopType(
          this,
          options,
          getItemInfoShop()
        ));
      }


      if (UIUtils.isInside(itemBalance.getSlot(), rows)) {
        template.set(itemBalance.getSlot(), CobbleShop.lang.getGlobalItemBalance(itemBalance).getButton(action -> {

        }));
      }


      ItemModel itemClose = CobbleShop.lang.getGlobalItemClose(this.itemClose);
      Button closeButton = itemClose.getButton(1, action -> {
        if (!this.getCloseCommand().isEmpty()) {
          PlayerUtils.executeCommand(
            shop.getCloseCommand(),
            player
          );
          return;
        }
        if (shop == null) {
          ShopApi.getConfig(options).open(player, options);
        } else {
          shop.open(player, options, config, 0, this);
        }
      });
      if (UIUtils.isInside(itemClose.getSlot(), rows))
        template.set(this.itemClose.getSlot(), closeButton);

      if (hasEnoughtButtons) {
        ItemModel previous = CobbleShop.lang.getGlobalItemPrevious(this.itemPrevious);
        if (UIUtils.isInside(itemPrevious.getSlot(), rows)) {
          template.set(itemPrevious.getSlot(), LinkedPageButton.builder()
            .display(previous.getItemStack())
            .linkType(LinkType.Previous)
            .build());
        }

        ItemModel next = CobbleShop.lang.getGlobalItemNext(this.itemNext);
        if (UIUtils.isInside(itemNext.getSlot(), rows)) {
          template.set(itemNext.getSlot(), LinkedPageButton.builder()
            .display(next.getItemStack())
            .linkType(LinkType.Next)
            .build());
        }

      }


      GooeyPage page;

      if (hasEnoughtButtons || autoPlace) {
        rectangle.apply(template);

        LinkedPage.Builder linkedPage = LinkedPage.builder()
          .template(template)
          .title(AdventureTranslator.toNative(title.replace("%shop%", id)));

        page = PaginationHelper.createPagesFromPlaceholders(template, buttons, linkedPage);
      } else {
        page = GooeyPage.builder()
          .template(template)
          .build();
        page.setTitle(AdventureTranslator.toNative(title.replace("%shop%", id)));
      }


      UIManager.openUIForcefully(player, page);
    } catch (Exception e) {
      e.printStackTrace();
      PlayerUtils.sendMessage(
        player,
        "Please contact the server administrator to report this error. Shop -> " + id + " Mod -> " + options.getModId(),
        CobbleShop.lang.getPrefix(),
        TypeMessage.CHAT
      );
    }
  }

}
