package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyManagerIntegrationTest {

    private GalaxyManager manager;
    private GalaxyConfig config;
    private static final long SEED = 12345L;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.targetStarCount = 10000;
        config.radiusLY = 5000f;
        config.chunkSizeLY = 100f;
        config.maxLoadedChunks = 64;
        config.nebulaCount = 20;
        manager = new GalaxyManager(SEED, config);
    }

    @Test
    void updateViewLoadsStarsNearCentre() {
        manager.updateView(0, 0, 300f);
        List<StarPosition> stars = collectStars();
        assertTrue(stars.size() > 0, "Core view should contain stars");
    }

    @Test
    void coreHasMoreStarsThanRim() {
        manager.updateView(0, 0, 200f);
        int coreStars = collectStars().size();

        GalaxyManager rimManager = new GalaxyManager(SEED, config);
        rimManager.updateView(config.radiusLY * 0.8, 0, 200f);
        int rimStars = collectStars(rimManager).size();

        assertTrue(coreStars > rimStars,
            "Core stars (" + coreStars + ") should exceed rim stars (" + rimStars + ")");
    }

    @Test
    void nebulaeGeneratedOnConstruction() {
        Array<NebulaRegion> nebulae = manager.getNebulae();
        assertEquals(config.nebulaCount, nebulae.size);
    }

    @Test
    void regionClassificationWorks() {
        assertEquals(GalaxyRegion.CORE, manager.getRegion(0, 0));
        assertEquals(GalaxyRegion.VOID, manager.getRegion(config.radiusLY * 2, 0));
    }

    @Test
    void findNearestStarReturnsResult() {
        manager.updateView(0, 0, 300f);
        StarPosition nearest = manager.findNearestStar(0, 0);
        assertNotNull(nearest, "Should find a star near galaxy centre");
    }

    @Test
    void sameSeedProducesIdenticalGalaxy() {
        manager.updateView(0, 0, 300f);
        List<StarPosition> starsA = collectStars();

        GalaxyManager manager2 = new GalaxyManager(SEED, config);
        manager2.updateView(0, 0, 300f);
        List<StarPosition> starsB = collectStars(manager2);

        assertEquals(starsA.size(), starsB.size());
        for (int i = 0; i < starsA.size(); i++) {
            assertEquals(starsA.get(i).uniqueId, starsB.get(i).uniqueId);
        }
    }

    @Test
    void galaxySeedAccessible() {
        assertEquals(SEED, manager.getGalaxySeed());
    }

    private List<StarPosition> collectStars() {
        return collectStars(manager);
    }

    private List<StarPosition> collectStars(GalaxyManager mgr) {
        List<StarPosition> list = new ArrayList<>();
        for (StarPosition s : mgr.getLoadedStars()) {
            list.add(s);
        }
        return list;
    }
}
