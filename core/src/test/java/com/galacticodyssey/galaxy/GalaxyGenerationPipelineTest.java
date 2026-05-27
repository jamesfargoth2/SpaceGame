package com.galacticodyssey.galaxy;

import com.galacticodyssey.data.GameSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalaxyGenerationPipelineTest {

    private static GameSession session(long seed) {
        return new GameSession(seed, "TestGalaxy",
            GalaxyType.SPIRAL, GalaxySize.SMALL, StartingRegion.INNER_RIM);
    }

    @Test
    void sameSeedPicksSameStartingSystemAndPlanet() {
        GameSession s1 = session(777L);
        GameSession s2 = session(777L);
        GalaxyGenerationPipeline.run(s1);
        GalaxyGenerationPipeline.run(s2);
        assertEquals(s1.startingSystem.uniqueId, s2.startingSystem.uniqueId,
            "Same seed must select the same star");
        assertEquals(s1.startingPlanet.seed, s2.startingPlanet.seed,
            "Same seed must select the same planet");
        assertEquals(s1.terrainSeed, s2.terrainSeed,
            "Same seed must derive the same terrain seed");
    }

    @Test
    void pipelineAlwaysProducesStartingPlanet() {
        for (long seed = 1L; seed <= 20L; seed++) {
            GameSession s = session(seed);
            GalaxyGenerationPipeline.run(s);
            assertNotNull(s.startingPlanet, "No planet for seed " + seed);
            assertNotNull(s.playerSpawnPos, "No spawn pos for seed " + seed);
            assertNotNull(s.shipSpawnPos, "No ship spawn pos for seed " + seed);
        }
    }

    @Test
    void pipelineLogsAllFivePhases() {
        GameSession s = session(42L);
        GalaxyGenerationPipeline.run(s);
        assertEquals(6, s.log.size(), "Expected exactly 6 log entries");
    }

    @Test
    void differentSeedsProduceDifferentTerrainSeeds() {
        GameSession s1 = session(1L);
        GameSession s2 = session(2L);
        GalaxyGenerationPipeline.run(s1);
        GalaxyGenerationPipeline.run(s2);
        assertNotEquals(s1.terrainSeed, s2.terrainSeed,
            "Different seeds should produce different terrain seeds");
    }

    @Test
    void shipSpawnIs75mEastOfPlayer() {
        GameSession s = session(100L);
        GalaxyGenerationPipeline.run(s);
        assertEquals(75f, s.shipSpawnPos.x - s.playerSpawnPos.x, 0.01f,
            "Ship should be 75m east of player");
        assertEquals(0f, s.shipSpawnPos.z - s.playerSpawnPos.z, 0.01f,
            "Ship Z should match player Z");
    }
}
