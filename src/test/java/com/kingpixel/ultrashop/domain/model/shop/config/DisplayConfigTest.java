package com.kingpixel.ultrashop.domain.model.shop.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DisplayConfig}.
 *
 * <p>Validates Lombok-generated builder, immutability, and value-equality semantics.
 * These tests pin down the contract that downstream code (Shop, adapters) relies on.</p>
 */
class DisplayConfigTest {

    @Test
    void builder_setsAllProvidedFields() {
        DisplayConfig cfg = DisplayConfig.builder()
            .name("weapons")
            .title("§6Weapons")
            .autoPlace(true)
            .rows(6)
            .colorProduct("§e")
            .build();

        assertEquals("weapons", cfg.getName());
        assertEquals("§6Weapons", cfg.getTitle());
        assertTrue(cfg.isAutoPlace());
        assertEquals(6, cfg.getRows());
        assertEquals("§e", cfg.getColorProduct());
    }

    @Test
    void builder_unsetFields_areNullOrZero() {
        DisplayConfig cfg = DisplayConfig.builder().name("x").build();

        assertNull(cfg.getTitle());
        assertNull(cfg.getRectangle());
        assertNull(cfg.getDisplayItem());
        assertNull(cfg.getPanels());
        assertFalse(cfg.isAutoPlace());
        assertEquals(0, cfg.getRows());
    }

    @Test
    void equality_isStructural() {
        DisplayConfig a = DisplayConfig.builder().name("x").rows(3).build();
        DisplayConfig b = DisplayConfig.builder().name("x").rows(3).build();
        DisplayConfig c = DisplayConfig.builder().name("y").rows(3).build();

        assertEquals(a, b, "VOs with same field values must be equal");
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toBuilder_producesEqualCopy() {
        DisplayConfig original = DisplayConfig.builder()
            .name("shop")
            .rows(6)
            .autoPlace(true)
            .build();

        DisplayConfig copy = original.toBuilder().build();

        assertEquals(original, copy);
        assertNotSame(original, copy);
    }

    @Test
    void toBuilder_allowsPartialUpdate() {
        DisplayConfig original = DisplayConfig.builder().name("a").rows(3).build();
        DisplayConfig updated = original.toBuilder().rows(6).build();

        assertEquals("a", updated.getName(), "name preserved");
        assertEquals(6, updated.getRows(), "rows updated");
        assertNotEquals(original, updated);
    }
}

