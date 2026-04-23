package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.shop.config.ConditionsConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.EconomyConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

  protected DisplayConfig displayConfig;
  protected EconomyConfig economyConfig;
  protected ConditionsConfig conditionsConfig;
  protected SoundConfig soundConfig;
}



