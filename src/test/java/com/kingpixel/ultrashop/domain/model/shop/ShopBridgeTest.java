package com.kingpixel.ultrashop.domain.model.shop;

import com.kingpixel.ultrashop.domain.model.RotationSchedule;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.SubShop;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ShopBridge} — the data-loss-prevention layer between the
 * legacy Shop class and the new sealed hierarchy.
 *
 * <p>These tests pin down the contract that NO field is silently dropped during
 * the round trip Legacy → New → Legacy.</p>
 */
class ShopBridgeTest {

    // --- fromLegacy: type inference ---------------------------------------

    @Test
    void emptyLegacyShop_inferredAsNormal() {
        com.kingpixel.ultrashop.domain.model.Shop legacy = new com.kingpixel.ultrashop.domain.model.Shop();
        legacy.setId("empty");

        Shop converted = ShopBridge.fromLegacy(legacy);

        assertInstanceOf(NormalShop.class, converted);
        assertEquals("empty", converted.getId());
    }

    @Test
    void legacyWithRotationSchedule_inferredAsRotation() {
        com.kingpixel.ultrashop.domain.model.Shop legacy = new com.kingpixel.ultrashop.domain.model.Shop();
        legacy.setId("daily");
        legacy.setRotationSchedule(new RotationSchedule("1h", 5));

        Shop converted = ShopBridge.fromLegacy(legacy);

        assertInstanceOf(RotationShop.class, converted);
        RotationShop r = (RotationShop) converted;
        assertEquals(5, r.getRotationAmount(), "amount must be preserved from legacy");
        assertInstanceOf(DurationScheduler.class, r.getScheduler());
    }

    @Test
    void legacyWithSubShops_inferredAsCategory() {
        com.kingpixel.ultrashop.domain.model.Shop legacy = new com.kingpixel.ultrashop.domain.model.Shop();
        legacy.setId("menu");
        legacy.setSubShops(List.of(new SubShop(0, "weapons"), new SubShop(1, "armor")));

        Shop converted = ShopBridge.fromLegacy(legacy);

        assertInstanceOf(CategoryShop.class, converted);
        assertEquals(2, ((CategoryShop) converted).getSubShops().size());
    }

    @Test
    void explicitTypeOverridesInference() {
        com.kingpixel.ultrashop.domain.model.Shop legacy = new com.kingpixel.ultrashop.domain.model.Shop();
        legacy.setId("explicit");
        legacy.setType(ShopType.CATEGORY);
        // Even with a rotationSchedule, the explicit type wins.
        legacy.setRotationSchedule(new RotationSchedule("1h", 3));

        Shop converted = ShopBridge.fromLegacy(legacy);

        assertInstanceOf(CategoryShop.class, converted);
    }

    @Test
    void nullLegacy_throws() {
        assertThrows(IllegalArgumentException.class, () -> ShopBridge.fromLegacy(null));
    }

    // --- fromLegacy: scheduler bridging -----------------------------------

    @Test
    void legacyCronSchedule_convertsToCronScheduler() {
        com.kingpixel.ultrashop.domain.model.Shop legacy = new com.kingpixel.ultrashop.domain.model.Shop();
        legacy.setId("weekly");
        RotationSchedule sched = new RotationSchedule();
        sched.setCron("0 18 * * 5");
        sched.setAmount(2);
        legacy.setRotationSchedule(sched);

        Shop converted = ShopBridge.fromLegacy(legacy);

        RotationShop r = assertInstanceOf(RotationShop.class, converted);
        CronScheduler cron = assertInstanceOf(CronScheduler.class, r.getScheduler());
        assertEquals("0 18 * * 5", cron.getExpression());
    }

    // --- fromLegacy → toLegacy: round trip --------------------------------

    @Test
    void roundTrip_normalShop_preservesId() {
        com.kingpixel.ultrashop.domain.model.Shop original = new com.kingpixel.ultrashop.domain.model.Shop();
        original.setId("weapons");

        Shop intermediate = ShopBridge.fromLegacy(original);
        com.kingpixel.ultrashop.domain.model.Shop back = ShopBridge.toLegacy(intermediate);

        assertEquals(original.getId(), back.getId());
        assertEquals(ShopType.NORMAL, back.getType());
    }

    @Test
    void roundTrip_rotationShop_preservesSchedulerAndAmount() {
        com.kingpixel.ultrashop.domain.model.Shop original = new com.kingpixel.ultrashop.domain.model.Shop();
        original.setId("daily");
        original.setRotationSchedule(new RotationSchedule("30m", 7));

        Shop intermediate = ShopBridge.fromLegacy(original);
        com.kingpixel.ultrashop.domain.model.Shop back = ShopBridge.toLegacy(intermediate);

        assertEquals(ShopType.ROTATION, back.getType());
        assertNotNull(back.getRotationSchedule());
        assertEquals("30m", back.getRotationSchedule().getInterval());
        assertEquals(7, back.getRotationSchedule().getAmount());
    }
}

