package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NebulaPlacerTest {

    private GalaxyConfig config;
    private NebulaPlacer placer;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.nebulaCount = 20;
        config.radiusLY = 5000f;
        placer = new NebulaPlacer();
    }

    @Test
    void placesCorrectNumberOfNebulae() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        assertEquals(config.nebulaCount, nebulae.size);
    }

    @Test
    void nebulaePlacedWithinGalaxyDisk() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            double dist = Math.sqrt(n.centreX * n.centreX + n.centreY * n.centreY);
            assertTrue(dist < config.radiusLY * 1.5f,
                "Nebula at distance " + dist + " too far from galaxy centre");
        }
    }

    @Test
    void allNebulaTypesRepresented() {
        config.nebulaCount = 200;
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        boolean[] seen = new boolean[NebulaType.values().length];
        for (NebulaRegion n : nebulae) {
            seen[n.type.ordinal()] = true;
        }
        for (NebulaType type : NebulaType.values()) {
            assertTrue(seen[type.ordinal()], "Nebula type " + type + " never appeared");
        }
    }

    @Test
    void nebulaeAreDeterministic() {
        Array<NebulaRegion> a = placer.place(config, 42L);
        Array<NebulaRegion> b = placer.place(config, 42L);
        assertEquals(a.size, b.size);
        for (int i = 0; i < a.size; i++) {
            assertEquals(a.get(i).centreX, b.get(i).centreX, 1e-6);
            assertEquals(a.get(i).centreY, b.get(i).centreY, 1e-6);
            assertEquals(a.get(i).type, b.get(i).type);
        }
    }

    @Test
    void nebulaeHavePositiveRadius() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            assertTrue(n.radiusLY > 0f, "Nebula radius must be positive");
        }
    }

    @Test
    void nebulaeHaveNonNullColour() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            assertNotNull(n.colour);
        }
    }
}
