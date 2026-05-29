package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.JsonReader;
import com.galacticodyssey.galaxy.faction.FactionData;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;

class ShipFactoryStyleTest {

    private static HullStyleRegistry registry() {
        HullStyleRegistry reg = new HullStyleRegistry();
        JsonReader r = new JsonReader();
        reg.loadStyles(r.parse(
            "[{\"id\":\"vaun\",\"sectionExponentMin\":3.5},{\"id\":\"federation\",\"sectionExponentMin\":2.0}]"));
        reg.loadEthosMap(r.parse("{\"MILITARIST\":\"vaun\",\"FEDERATION\":\"federation\"}"));
        reg.loadFactionMap(r.parse("{}"));
        return reg;
    }

    @Test
    void resolvesStyleForFactionConfig() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(1L, ShipSizeClass.SMALL);
        c.faction = new FactionData("faction-1", "N", 0, 0, 0, 0.5f, 0.5f,
                FactionEthos.MILITARIST, 0.5f, 0.5f, 0.5f, 300f, "HUMAN_COLONY");
        HullStyle s = ShipFactory.resolveStyle(registry(), c);
        assertEquals("vaun", s.id);
    }

    @Test
    void resolvesDefaultWhenRegistryNull() {
        ShipGenerationConfig c = ShipGenerationConfig.defaults(1L, ShipSizeClass.SMALL);
        HullStyle s = ShipFactory.resolveStyle(null, c);
        assertEquals("default", s.id);
    }
}
