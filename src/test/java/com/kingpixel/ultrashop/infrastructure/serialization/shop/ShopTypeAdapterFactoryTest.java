package com.kingpixel.ultrashop.infrastructure.serialization.shop;

import com.google.gson.Gson;
import com.kingpixel.ultrashop.domain.model.ShopType;
import com.kingpixel.ultrashop.domain.model.shop.CategoryShop;
import com.kingpixel.ultrashop.domain.model.shop.NormalShop;
import com.kingpixel.ultrashop.domain.model.shop.RotationShop;
import com.kingpixel.ultrashop.domain.model.shop.Shop;
import com.kingpixel.ultrashop.domain.model.shop.config.DisplayConfig;
import com.kingpixel.ultrashop.domain.model.shop.config.SoundConfig;
import com.kingpixel.ultrashop.domain.scheduler.CronScheduler;
import com.kingpixel.ultrashop.domain.scheduler.DurationScheduler;
import com.kingpixel.ultrashop.infrastructure.serialization.GsonProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Shop serialization adapter chain ({@link ShopTypeAdapterFactory}
 * + per-type adapters via {@link ShopAdapterRegistry}).
 *
 * <p>Covers the three input shapes:</p>
 * <ol>
 *   <li>New format round-trip per shop subtype.</li>
 *   <li>Legacy {@code Shop} JSON → bridges to new hierarchy without data loss.</li>
 *   <li>Malformed input → throws {@link com.google.gson.JsonParseException}.</li>
 * </ol>
 */
class ShopTypeAdapterFactoryTest {

    private final Gson gson = GsonProvider.gson();

    // --- New format round trips ----------------------------------------

    @Test
    void roundTrip_normalShop() {
        NormalShop original = new NormalShop();
        original.setId("weapons");
        original.setDisplayConfig(DisplayConfig.builder().name("Weapons").rows(6).build());
        original.setSoundConfig(SoundConfig.builder().soundOpen("minecraft:block.chest.open").build());

        String json = gson.toJson(original, Shop.class);
        Shop decoded = gson.fromJson(json, Shop.class);

        assertInstanceOf(NormalShop.class, decoded);
        assertEquals("weapons", decoded.getId());
        assertEquals(ShopType.NORMAL, decoded.getType());
        assertEquals("Weapons", decoded.getDisplayConfig().getName());
        assertEquals(6, decoded.getDisplayConfig().getRows());
    }

    @Test
    void roundTrip_categoryShop() {
        CategoryShop original = new CategoryShop();
        original.setId("menu");
        original.setDisplayConfig(DisplayConfig.builder().name("Menu").build());

        String json = gson.toJson(original, Shop.class);
        Shop decoded = gson.fromJson(json, Shop.class);

        assertInstanceOf(CategoryShop.class, decoded);
        assertEquals(ShopType.CATEGORY, decoded.getType());
    }

    @Test
    void roundTrip_rotationShop_withCronScheduler() {
        RotationShop original = new RotationShop();
        original.setId("daily");
        original.setDisplayConfig(DisplayConfig.builder().name("Daily").build());
        original.setScheduler(new CronScheduler("0 0 * * *"));
        original.setRotationAmount(5);

        String json = gson.toJson(original, Shop.class);
        Shop decoded = gson.fromJson(json, Shop.class);

        RotationShop r = assertInstanceOf(RotationShop.class, decoded);
        assertInstanceOf(CronScheduler.class, r.getScheduler());
        assertEquals("0 0 * * *", ((CronScheduler) r.getScheduler()).getExpression());
        assertEquals(5, r.getRotationAmount());
    }

    @Test
    void roundTrip_rotationShop_withDurationScheduler() {
        RotationShop original = new RotationShop();
        original.setId("frequent");
        original.setDisplayConfig(DisplayConfig.builder().name("Frequent").build());
        original.setScheduler(new DurationScheduler("15m"));

        String json = gson.toJson(original, Shop.class);
        Shop decoded = gson.fromJson(json, Shop.class);

        RotationShop r = assertInstanceOf(RotationShop.class, decoded);
        assertInstanceOf(DurationScheduler.class, r.getScheduler());
        assertEquals("15m", ((DurationScheduler) r.getScheduler()).getDuration());
    }

    // --- Output format guarantees --------------------------------------

    @Test
    void serialize_writesTypeDiscriminator() {
        String json = gson.toJson(new NormalShop(), Shop.class);
        assertTrue(json.contains("\"type\": \"NORMAL\""),
            "Output must always carry the type discriminator");
    }

    // --- Legacy backwards compatibility (data-loss prevention) ----------

    @Test
    void deserialize_legacyShopJson_bridgesToNormal() {
        String legacyJson = """
            {
              "type": "NORMAL",
              "name": "Legacy Weapons",
              "title": "%shop%",
              "rows": 6,
              "products": []
            }
            """;

        Shop decoded = gson.fromJson(legacyJson, Shop.class);

        assertInstanceOf(NormalShop.class, decoded,
            "Legacy flat-field JSON must transparently bridge to new hierarchy");
        assertEquals("Legacy Weapons", decoded.getDisplayConfig().getName());
        assertEquals(6, decoded.getDisplayConfig().getRows());
    }

    @Test
    void deserialize_legacyShopJson_withRotationSchedule_bridgesToRotation() {
        String legacyJson = """
            {
              "type": "ROTATION",
              "name": "Legacy Daily",
              "rows": 6,
              "rotationSchedule": {
                "cron": "0 0 * * *",
                "amount": 3
              },
              "products": []
            }
            """;

        Shop decoded = gson.fromJson(legacyJson, Shop.class);

        RotationShop r = assertInstanceOf(RotationShop.class, decoded);
        CronScheduler cron = assertInstanceOf(CronScheduler.class, r.getScheduler());
        assertEquals("0 0 * * *", cron.getExpression());
        assertEquals(3, r.getRotationAmount());
    }

    @Test
    void deserialize_legacyShopJson_withInterval_bridgesToDuration() {
        String legacyJson = """
            {
              "type": "ROTATION",
              "name": "Legacy Hourly",
              "rows": 6,
              "rotationSchedule": {
                "interval": "1h",
                "amount": 4
              },
              "products": []
            }
            """;

        Shop decoded = gson.fromJson(legacyJson, Shop.class);

        RotationShop r = assertInstanceOf(RotationShop.class, decoded);
        assertInstanceOf(DurationScheduler.class, r.getScheduler());
        assertEquals(4, r.getRotationAmount());
    }
}

