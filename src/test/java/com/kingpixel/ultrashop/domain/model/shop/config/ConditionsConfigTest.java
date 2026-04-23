package com.kingpixel.ultrashop.domain.model.shop.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConditionsConfig}.
 */
class ConditionsConfigTest {

    @Test
    void builder_setsAllProvidedFields() {
        ConditionsConfig cfg = ConditionsConfig.builder()
            .openConditions(List.of())
            .closeCommand("say bye")
            .announceRotation(true)
            .build();

        assertEquals(List.of(), cfg.getOpenConditions());
        assertEquals("say bye", cfg.getCloseCommand());
        assertTrue(cfg.isAnnounceRotation());
    }

    @Test
    void closeCommand_isNullable() {
        ConditionsConfig cfg = ConditionsConfig.builder()
            .openConditions(List.of())
            .build();

        assertNull(cfg.getCloseCommand());
        assertFalse(cfg.isAnnounceRotation());
    }

    @Test
    void equality_isStructural() {
        ConditionsConfig a = ConditionsConfig.builder()
            .openConditions(List.of())
            .closeCommand("cmd")
            .announceRotation(false)
            .build();
        ConditionsConfig b = ConditionsConfig.builder()
            .openConditions(List.of())
            .closeCommand("cmd")
            .announceRotation(false)
            .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

