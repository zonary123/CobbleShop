package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.Scheduler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the sealed {@link Shop} hierarchy and {@link ShopVisitor} dispatch.
 */
class ShopHierarchyTest {

    @Test
    void normalShop_hasNormalType() {
        assertEquals(ShopType.NORMAL, new NormalShop().getType());
    }

    @Test
    void categoryShop_hasCategoryType() {
        assertEquals(ShopType.CATEGORY, new CategoryShop().getType());
    }

    @Test
    void rotationShop_hasRotationType() {
        assertEquals(ShopType.ROTATION, new RotationShop().getType());
    }

    @Test
    void rotationShop_defaultsToProjectScheduler() {
        Scheduler s = new RotationShop().getScheduler();
        assertInstanceOf(CronScheduler.class, s,
            "Project default must be CRON per the SchedulerType convention");
    }

    @Test
    void rotationShop_check_replacesNullSchedulerWithDefault() {
        RotationShop shop = new RotationShop();
        shop.setScheduler(null);
        shop.check();
        assertNotNull(shop.getScheduler());
    }

    @Test
    void rotationShop_check_clampsRotationAmountToOne() {
        RotationShop shop = new RotationShop();
        shop.setRotationAmount(0);
        shop.check();
        assertEquals(1, shop.getRotationAmount());
    }

    @Test
    void categoryShop_activeProducts_alwaysEmpty() {
        assertTrue(new CategoryShop().activeProducts().isEmpty());
    }

    @Test
    void visitor_dispatchesToCorrectMethod() {
        ShopVisitor<String> namingVisitor = new ShopVisitor<>() {
            @Override public String visit(NormalShop s)   { return "normal"; }
            @Override public String visit(CategoryShop s) { return "category"; }
            @Override public String visit(RotationShop s) { return "rotation"; }
        };

        assertEquals("normal",   new NormalShop().accept(namingVisitor));
        assertEquals("category", new CategoryShop().accept(namingVisitor));
        assertEquals("rotation", new RotationShop().accept(namingVisitor));
    }

    @Test
    void visitor_canReturnVoid() {
        int[] counter = new int[3];
        ShopVisitor<Void> counting = new ShopVisitor<>() {
            @Override public Void visit(NormalShop s)   { counter[0]++; return null; }
            @Override public Void visit(CategoryShop s) { counter[1]++; return null; }
            @Override public Void visit(RotationShop s) { counter[2]++; return null; }
        };

        new NormalShop().accept(counting);
        new NormalShop().accept(counting);
        new CategoryShop().accept(counting);
        new RotationShop().accept(counting);

        assertArrayEquals(new int[]{2, 1, 1}, counter);
    }
}


