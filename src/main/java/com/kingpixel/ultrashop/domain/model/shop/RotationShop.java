package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.UltraShop;
import com.kingpixel.ultrashop.domain.model.DynamicRotation;
import com.kingpixel.ultrashop.domain.model.Product;
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

  private List<Product> productPool;
  private Scheduler scheduler;
  private int rotationAmount;

  /** Runtime state — never serialized with the shop definition. */
  private transient DynamicRotation currentRotation;

  public RotationShop() {
    super();
    this.productPool = new ArrayList<>();
    this.rotationAmount = 3;
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
    if (productPool == null) productPool = new ArrayList<>();
    if (rotationAmount < 1) rotationAmount = 1;
    if (scheduler == null) {
      UltraShop.LOGGER.warn("RotationShop '{}' has no scheduler — falling back to default.", getId());
      scheduler = Scheduler.defaultScheduler();
    }
  }
}

