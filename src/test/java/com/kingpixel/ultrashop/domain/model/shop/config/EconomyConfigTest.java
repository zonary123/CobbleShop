package com.kingpixel.ultrashop.domain.model.shop.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EconomyConfig}.
 */
class EconomyConfigTest {

    @Test
    void builder_setsAllProvidedFields() {
        EconomyConfig cfg = EconomyConfig.builder()
            .economies(new LinkedHashSet<>())
            .globalDiscount(0.1f)
            .discounts(Map.of("group.vip", 2.0f))
            .build();

        assertNotNull(cfg.getEconomies());
        assertEquals(0.1f, cfg.getGlobalDiscount());
        assertEquals(2.0f, cfg.getDiscounts().get("group.vip"));
    }

    @Test
    void equality_isStructural() {
        EconomyConfig a = EconomyConfig.builder()
            .economies(new LinkedHashSet<>())
            .globalDiscount(0.5f)
            .discounts(Map.of("k", 1.0f))
            .build();
        EconomyConfig b = EconomyConfig.builder()
            .economies(new LinkedHashSet<>())
            .globalDiscount(0.5f)
            .discounts(Map.of("k", 1.0f))
            .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toBuilder_allowsPartialUpdate() {
        EconomyConfig original = EconomyConfig.builder()
            .economies(new LinkedHashSet<>())
            .globalDiscount(0.0f)
            .discounts(Map.of())
            .build();
        EconomyConfig updated = original.toBuilder().globalDiscount(0.25f).build();

        assertEquals(0.25f, updated.getGlobalDiscount());
        assertEquals(original.getDiscounts(), updated.getDiscounts());
    }
}

