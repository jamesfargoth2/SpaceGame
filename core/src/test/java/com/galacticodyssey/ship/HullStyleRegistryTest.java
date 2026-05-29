package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.badlogic.gdx.utils.JsonReader;
import com.galacticodyssey.galaxy.faction.FactionData;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;

class HullStyleRegistryTest {

    private static final String STYLES_JSON =
        "[" +
        "  {\"id\":\"vaun\",\"generatorType\":\"LOFTED\",\"sectionExponentMin\":3.5,\"sectionExponentMax\":4.5," +
        "   \"aspectBiasMin\":0.8,\"aspectBiasMax\":1.0,\"spineCurvature\":0.3,\"panelInsetScale\":0.03," +
        "   \"ageless\":false,\"baseColors\":[[0.2,0.2,0.22]],\"accentColors\":[[0.8,0.1,0.1]],\"glowColors\":[[1.0,0.4,0.1]]}," +
        "  {\"id\":\"federation\",\"generatorType\":\"LOFTED\",\"sectionExponentMin\":2.0,\"sectionExponentMax\":2.8," +
        "   \"aspectBiasMin\":0.9,\"aspectBiasMax\":1.1,\"spineCurvature\":1.4,\"panelInsetScale\":0.01," +
        "   \"ageless\":false,\"baseColors\":[[0.85,0.87,0.9]],\"accentColors\":[[0.2,0.5,0.95]],\"glowColors\":[[0.3,0.6,1.0]]}" +
        "]";

    private static final String ETHOS_JSON =
        "{\"FEDERATION\":\"federation\",\"MILITARIST\":\"vaun\"}";

    private static final String FACTION_JSON =
        "{\"vaun_empire\":\"vaun\"}";

    private static HullStyleRegistry loaded() {
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(STYLES_JSON));
        reg.loadEthosMap(r.parse(ETHOS_JSON));
        reg.loadFactionMap(r.parse(FACTION_JSON));
        return reg;
    }

    private static FactionData faction(String id, FactionEthos ethos) {
        return new FactionData(id, "Name", 0, 0, 0, 0.5f, 0.5f, ethos,
                0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
    }

    @Test
    void getReturnsLoadedStyleById() {
        HullStyle s = loaded().get("vaun");
        assertNotNull(s);
        assertEquals("vaun", s.id);
        assertEquals(GeneratorType.LOFTED, s.generatorType);
        assertEquals(3.5f, s.sectionExponentMin, 1e-4);
    }

    @Test
    void resolveUsesExplicitFactionMapFirst() {
        HullStyle s = loaded().resolve(faction("vaun_empire", FactionEthos.FEDERATION));
        assertEquals("vaun", s.id);
    }

    @Test
    void resolveFallsBackToEthosMap() {
        HullStyle s = loaded().resolve(faction("faction-3", FactionEthos.FEDERATION));
        assertEquals("federation", s.id);
    }

    @Test
    void resolveNullFactionReturnsDefault() {
        HullStyle s = loaded().resolve(null);
        assertEquals("default", s.id);
    }

    @Test
    void resolveUnknownEthosReturnsDefault() {
        HullStyle s = loaded().resolve(faction("faction-9", FactionEthos.CORPORATE));
        assertEquals("default", s.id);
    }

    @Test
    void allEthosFallbacksResolveToALoadedStyle() {
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(
            "[{\"id\":\"federation\"},{\"id\":\"vaun\"},{\"id\":\"zulkiri\"}," +
            "{\"id\":\"independent_utilitarian\"},{\"id\":\"pirate_patchwork\"}]"));
        reg.loadEthosMap(r.parse(
            "{\"FEDERATION\":\"federation\",\"MILITARIST\":\"vaun\",\"CORPORATE\":\"zulkiri\"," +
            "\"ISOLATIONIST\":\"independent_utilitarian\",\"PIRATE_SYNDICATE\":\"pirate_patchwork\"}"));
        reg.loadFactionMap(r.parse("{}"));
        for (FactionEthos ethos : FactionEthos.values()) {
            HullStyle s = reg.resolve(faction("faction-x", ethos));
            assertNotEquals("default", s.id,
                "ethos " + ethos + " should map to a real style");
        }
    }
}
