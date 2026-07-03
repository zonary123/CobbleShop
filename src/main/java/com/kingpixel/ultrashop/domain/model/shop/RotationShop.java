package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.DynamicRotation;
import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.RotationScope;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic-catalog shop. Owns a {@link #productPool} from which {@link #rotationAmount}
 * products are picked each rotation tick driven by {@link #scheduler}.
 *
 * <p>The current rotation state ({@link #currentRotation}) is {@code transient} —
 * it is recomputed on demand and persisted separately (today: {@code DataShop};
 * future: {@code RotationStateRepository} for cross-server consistency).</p>
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public final class RotationShop extends AbstractShop implements Shop {

  private List<Product> products;
  private Scheduler scheduler;
  private int rotationAmount;

  /**
   * Fixed GUI slots where rotated products are placed, in order.
   * When set, the i-th picked product uses {@code rotationSlots.get(i)}.
   */
  private List<Integer> rotationSlots;

  /**
   * {@link RotationScope#GLOBAL} — one catalog for everyone.
   * {@link RotationScope#PLAYER} — each player rotates on their own schedule.
   */
  private RotationScope rotationScope;

  /** Runtime state — never serialized with the shop definition. */
  private transient DynamicRotation currentRotation;

  public RotationShop() {
    super();
    this.products = new ArrayList<>();
    this.rotationAmount = 3;
    this.rotationSlots = new ArrayList<>();
    this.rotationScope = RotationScope.GLOBAL;
    this.scheduler = Scheduler.defaultScheduler();
  }

  @Override
  public ShopType getType() {
    return ShopType.ROTATION;
  }

  @Override
  public List<Product> activeProducts() {
    return currentRotation != null && currentRotation.getProducts() != null
      ? currentRotation.getProducts()
      : List.of();
  }

  @Override
  public <R> R accept(ShopVisitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void check() {
    checkConfigs();
    if (products == null) products = new ArrayList<>();
    products.forEach(p -> p.check(legacyView()));
    if (rotationAmount < 1) rotationAmount = 1;
    if (rotationSlots == null) rotationSlots = new ArrayList<>();
    if (rotationScope == null) rotationScope = RotationScope.GLOBAL;
    if (!rotationSlots.isEmpty()) {
      int maxSlot = displayConfig.getRows() * 9 - 1;
      rotationSlots.removeIf(s -> s == null || s < 0 || s > maxSlot);
    }
    if (scheduler == null) {
      UltraShop.LOGGER.warn("RotationShop '{}' has no scheduler — falling back to default.", getId());
      scheduler = Scheduler.defaultScheduler();
    }
  }

  public boolean isPlayerScoped() {
    return rotationScope == RotationScope.PLAYER;
  }

  private com.kingpixel.ultrashop.domain.model.Shop legacyView() {
    return ShopBridge.toLegacy(this);
  }
}

