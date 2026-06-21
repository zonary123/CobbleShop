package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.cobbleutils.Model.EconomyUse;
import com.kingpixel.cobbleutils.Model.ItemModel;
import com.kingpixel.cobbleutils.Model.PanelsConfig;
import com.kingpixel.cobbleutils.Model.Rectangle;
import com.kingpixel.cobbleutils.util.economys.providers.ImpactorEconomy;
import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Shared base for all {@link Shop} implementations. Holds the four config Value
 * Objects ({@link DisplayConfig}, {@link EconomyConfig}, {@link ConditionsConfig},
 * {@link SoundConfig}) plus the shop id, which are common to every shop type.
 *
 * <p>This class deliberately does NOT implement {@link Shop} — it is a code-reuse
 * helper. The three concrete classes ({@link NormalShop}, {@link CategoryShop},
 * {@link RotationShop}) extend this AND implement {@code Shop}, satisfying the
 * interface contract via the Lombok-generated getters declared here.</p>
 *
 * <p><b>Mutability:</b> setters are exposed because the admin GUI mutates shops
 * in place. The config VOs themselves are immutable — to change a single field,
 * use {@code displayConfig.toBuilder().rows(6).build()} and assign the result.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractShop {

  /** Transient: derived from the file name, never serialized. */
  protected transient String id;
  protected transient String filePath;

  protected DisplayConfig displayConfig;
  protected EconomyConfig economyConfig;
  protected ConditionsConfig conditionsConfig;
  protected SoundConfig soundConfig;
  protected boolean maintenance;
  protected String webhookUrl;
  protected Map<String, BigDecimal> dailySellLimits = new HashMap<>();
  protected String dailySellResetCooldown = "24h";

  public void checkConfigs() {
    if (displayConfig == null) {
      displayConfig = DisplayConfig.builder().build();
    }
    if (displayConfig.getName() == null) {
      displayConfig = displayConfig.toBuilder().name("Shop").build();
    }
    if (displayConfig.getTitle() == null) {
      displayConfig = displayConfig.toBuilder().title("%shop%").build();
    }
    if (displayConfig.getRows() <= 0) {
      displayConfig = displayConfig.toBuilder().rows(6).build();
    }
    if (displayConfig.getPanels() == null) {
      displayConfig = displayConfig.toBuilder().panels(List.of(
        new PanelsConfig(new ItemModel("minecraft:gray_stained_glass_pane"), displayConfig.getRows())
      )).build();
    }
    if (displayConfig.getRectangle() == null
        || displayConfig.getRectangle().getLength() <= 0
        || displayConfig.getRectangle().getWidth() <= 0) {
      displayConfig = displayConfig.toBuilder().rectangle(new Rectangle(1, 1, 4, 7)).build();
    }
    if (displayConfig.getItemInfoShop() == null) {
      ItemModel info = new ItemModel("");
      info.setSlot(51);
      displayConfig = displayConfig.toBuilder().itemInfoShop(info).build();
    }
    if (displayConfig.getItemBalance() == null) {
      ItemModel bal = new ItemModel("");
      bal.setSlot(47);
      displayConfig = displayConfig.toBuilder().itemBalance(bal).build();
    }
    if (displayConfig.getItemPrevious() == null) {
      ItemModel prev = new ItemModel("");
      prev.setSlot(45);
      displayConfig = displayConfig.toBuilder().itemPrevious(prev).build();
    }
    if (displayConfig.getItemClose() == null) {
      ItemModel close = new ItemModel("");
      close.setSlot(49);
      displayConfig = displayConfig.toBuilder().itemClose(close).build();
    }
    if (displayConfig.getItemNext() == null) {
      ItemModel next = new ItemModel("");
      next.setSlot(53);
      displayConfig = displayConfig.toBuilder().itemNext(next).build();
    }

    if (economyConfig == null) {
      economyConfig = EconomyConfig.builder().build();
    }
    if (economyConfig.getEconomies() == null || economyConfig.getEconomies().isEmpty()) {
      LinkedHashSet<EconomyUse> ecos = new LinkedHashSet<>();
      ecos.add(new EconomyUse(ImpactorEconomy.IDENTIFY, "impactor:dollars"));
      economyConfig = economyConfig.toBuilder().economies(ecos).build();
    }
    if (economyConfig.getDiscounts() == null) {
      economyConfig = economyConfig.toBuilder().discounts(new HashMap<>()).build();
    }

    if (conditionsConfig == null) {
      conditionsConfig = ConditionsConfig.builder().build();
    }
    if (conditionsConfig.getOpenConditions() == null) {
      conditionsConfig = conditionsConfig.toBuilder().openConditions(new ArrayList<>()).build();
    }

    if (soundConfig == null) {
      soundConfig = SoundConfig.builder()
        .soundOpen("minecraft:block.chest.open")
        .soundClose("minecraft:block.chest.close")
        .build();
    }
    if (soundConfig.getSoundOpen() == null) {
      soundConfig = soundConfig.toBuilder().soundOpen("minecraft:block.chest.open").build();
    }
    if (soundConfig.getSoundClose() == null) {
      soundConfig = soundConfig.toBuilder().soundClose("minecraft:block.chest.close").build();
    }
    if (dailySellLimits == null) {
      dailySellLimits = new HashMap<>();
    }
    if (dailySellResetCooldown == null || dailySellResetCooldown.isBlank()) {
      dailySellResetCooldown = "24h";
    }
  }
}



