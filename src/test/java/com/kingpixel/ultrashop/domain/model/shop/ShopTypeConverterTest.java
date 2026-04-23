package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.Product;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ShopTypeConverter} promotion / demotion semantics:
 * id and config VOs survive intact, products migrate between
 * {@code products} and {@code productPool} without loss, and round-trips
 * preserve the original payload.
 */
class ShopTypeConverterTest {

  @Test
  void promoteToRotation_movesProductsIntoPoolAndPreservesConfig() {
    NormalShop source = new NormalShop();
    source.setId("shop_a");
    source.setDisplayConfig(DisplayConfig.builder().name("Shop A").rows(6).build());
    List<Product> products = sampleProducts(5);
    source.setProducts(products);

    Scheduler scheduler = new CronScheduler("0 * * * *");
    RotationShop result = ShopTypeConverter.promoteToRotation(source, scheduler, 3);

    assertEquals("shop_a", result.getId());
    assertSame(source.getDisplayConfig(), result.getDisplayConfig());
    assertSame(products, result.getProductPool());
    assertSame(scheduler, result.getScheduler());
    assertEquals(3, result.getRotationAmount());
  }

  @Test
  void promoteToRotation_clampsAmountToOneWhenZeroOrNegative() {
    NormalShop source = new NormalShop();
    source.setId("shop_b");

    RotationShop result = ShopTypeConverter.promoteToRotation(source, new CronScheduler("0 * * * *"), 0);

    assertEquals(1, result.getRotationAmount());
  }

  @Test
  void demoteToNormal_exposesPoolAsCatalogAndDropsScheduler() {
    RotationShop source = new RotationShop();
    source.setId("shop_c");
    source.setDisplayConfig(DisplayConfig.builder().name("Shop C").build());
    List<Product> pool = sampleProducts(10);
    source.setProductPool(pool);
    source.setScheduler(new CronScheduler("0 12 * * *"));
    source.setRotationAmount(4);

    NormalShop result = ShopTypeConverter.demoteToNormal(source);

    assertEquals("shop_c", result.getId());
    assertSame(source.getDisplayConfig(), result.getDisplayConfig());
    assertSame(pool, result.getProducts());
  }

  @Test
  void roundTrip_promoteDemotePromote_preservesIdAndProducts() {
    NormalShop original = new NormalShop();
    original.setId("shop_d");
    original.setDisplayConfig(DisplayConfig.builder().name("Shop D").build());
    List<Product> products = sampleProducts(3);
    original.setProducts(products);

    Scheduler scheduler = new CronScheduler("*/15 * * * *");
    RotationShop promoted = ShopTypeConverter.promoteToRotation(original, scheduler, 2);
    NormalShop demoted = ShopTypeConverter.demoteToNormal(promoted);
    RotationShop rePromoted = ShopTypeConverter.promoteToRotation(demoted, scheduler, 2);

    assertEquals(original.getId(), rePromoted.getId());
    assertSame(original.getDisplayConfig(), rePromoted.getDisplayConfig());
    assertSame(products, rePromoted.getProductPool());
    assertEquals(2, rePromoted.getRotationAmount());
  }

  @Test
  void promoteToRotation_doesNotMutateSource() {
    NormalShop source = new NormalShop();
    source.setId("shop_e");
    List<Product> products = sampleProducts(2);
    source.setProducts(products);

    ShopTypeConverter.promoteToRotation(source, new CronScheduler("0 * * * *"), 3);

    assertEquals(ShopType.NORMAL, source.getType());
    assertSame(products, source.getProducts());
  }

  private static List<Product> sampleProducts(int count) {
    List<Product> products = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Product product = new Product();
      product.setProduct("minecraft:stone");
      products.add(product);
    }
    assertNotNull(products);
    assertTrue(products.size() == count);
    return products;
  }
}

