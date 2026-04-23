package com.kingpixel.ultrashop.domain.model.shop.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SoundConfig}.
 */
class SoundConfigTest {

    @Test
    void builder_setsBothFields() {
        SoundConfig cfg = SoundConfig.builder()
            .soundOpen("minecraft:block.chest.open")
            .soundClose("minecraft:block.chest.close")
            .build();

        assertEquals("minecraft:block.chest.open", cfg.getSoundOpen());
        assertEquals("minecraft:block.chest.close", cfg.getSoundClose());
    }

    @Test
    void bothSounds_areNullable() {
        SoundConfig cfg = SoundConfig.builder().build();

        assertNull(cfg.getSoundOpen());
        assertNull(cfg.getSoundClose());
    }

    @Test
    void equality_isStructural() {
        SoundConfig a = SoundConfig.builder().soundOpen("x").soundClose("y").build();
        SoundConfig b = SoundConfig.builder().soundOpen("x").soundClose("y").build();
        SoundConfig c = SoundConfig.builder().soundOpen("x").soundClose("z").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}

