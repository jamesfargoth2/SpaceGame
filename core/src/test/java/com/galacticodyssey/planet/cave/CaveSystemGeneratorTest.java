package com.galacticodyssey.planet.cave;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CaveSystemGeneratorTest {

    private static final long TEST_SEED = 42L;
    private CaveSystemGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new CaveSystemGenerator();
    }

    @Test
    void deterministic() {
        CaveSystemConfig cfg = new CaveSystemConfig(TEST_SEED,
                200f, 100f, 8, 10, 0.1f, true, true, true);

        GeneratedCaveSystem a = generator.generate(cfg);
        GeneratedCaveSystem b = new CaveSystemGenerator().generate(cfg);

        assertEquals(a.chambers.size(), b.chambers.size());
        assertEquals(a.tunnels.size(), b.tunnels.size());
        assertEquals(a.veins.size(), b.veins.size());
        assertEquals(a.bioluminescentPatches.size(), b.bioluminescentPatches.size());

        for (int i = 0; i < a.chambers.size(); i++) {
            assertEquals(a.chambers.get(i).cx, b.chambers.get(i).cx, 1e-6f);
            assertEquals(a.chambers.get(i).cy, b.chambers.get(i).cy, 1e-6f);
            assertEquals(a.chambers.get(i).cz, b.chambers.get(i).cz, 1e-6f);
            assertEquals(a.chambers.get(i).type, b.chambers.get(i).type);
        }
    }

    @Test
    void hasEntryChamber() {
        CaveSystemConfig cfg = new CaveSystemConfig(TEST_SEED,
                150f, 80f, 6, 8, 0.1f, false, false, false);

        GeneratedCaveSystem result = generator.generate(cfg);

        assertFalse(result.chambers.isEmpty(), "Should have at least one chamber");
        CaveChamber entry = result.chambers.get(0);
        assertEquals(ChamberType.ENTRY, entry.type, "First chamber should be ENTRY");
        assertEquals(0f, entry.cx, 1e-6f, "Entry chamber should be at x=0");
        assertEquals(-10f, entry.cy, 1e-6f, "Entry chamber should be near surface at y=-10");
        assertEquals(0f, entry.cz, 1e-6f, "Entry chamber should be at z=0");
    }

    @Test
    void allChambersConnected() {
        for (long seed = 0; seed < 20; seed++) {
            CaveSystemConfig cfg = new CaveSystemConfig(seed,
                    200f, 100f, 10, 12, 0.1f, false, false, false);

            GeneratedCaveSystem result = generator.generate(cfg);
            int n = result.chambers.size();
            if (n <= 1) continue;

            // Build adjacency from tunnels by matching start/end to chamber centres.
            Map<Integer, Set<Integer>> adj = new HashMap<>();
            for (int i = 0; i < n; i++) adj.put(i, new HashSet<>());

            for (BezierTunnel tunnel : result.tunnels) {
                int from = findChamber(result.chambers, tunnel.startX, tunnel.startY, tunnel.startZ);
                int to = findChamber(result.chambers, tunnel.endX, tunnel.endY, tunnel.endZ);
                if (from >= 0 && to >= 0) {
                    adj.get(from).add(to);
                    adj.get(to).add(from);
                }
            }

            // BFS from chamber 0.
            boolean[] visited = new boolean[n];
            Queue<Integer> queue = new LinkedList<>();
            queue.add(0);
            visited[0] = true;
            int visitCount = 1;
            while (!queue.isEmpty()) {
                int cur = queue.poll();
                for (int neighbour : adj.get(cur)) {
                    if (!visited[neighbour]) {
                        visited[neighbour] = true;
                        visitCount++;
                        queue.add(neighbour);
                    }
                }
            }
            assertEquals(n, visitCount,
                    "All chambers should be reachable via tunnels, seed=" + seed);
        }
    }

    @Test
    void tunnelsUseBezierCurves() {
        CaveSystemConfig cfg = new CaveSystemConfig(TEST_SEED,
                200f, 100f, 6, 8, 0.1f, false, false, false);

        GeneratedCaveSystem result = generator.generate(cfg);
        assertFalse(result.tunnels.isEmpty(), "Should have tunnels");

        for (BezierTunnel t : result.tunnels) {
            // At least one control point should differ from the straight-line interpolation.
            float lerpX1 = t.startX + (t.endX - t.startX) * 0.33f;
            float lerpY1 = t.startY + (t.endY - t.startY) * 0.33f;
            float lerpZ1 = t.startZ + (t.endZ - t.startZ) * 0.33f;

            float d1 = Math.abs(t.ctrl1X - lerpX1) + Math.abs(t.ctrl1Y - lerpY1)
                    + Math.abs(t.ctrl1Z - lerpZ1);

            float lerpX2 = t.startX + (t.endX - t.startX) * 0.66f;
            float lerpY2 = t.startY + (t.endY - t.startY) * 0.66f;
            float lerpZ2 = t.startZ + (t.endZ - t.startZ) * 0.66f;

            float d2 = Math.abs(t.ctrl2X - lerpX2) + Math.abs(t.ctrl2Y - lerpY2)
                    + Math.abs(t.ctrl2Z - lerpZ2);

            assertTrue(d1 > 0.01f || d2 > 0.01f,
                    "Control points should differ from straight line (curve should bend)");
        }
    }

    @Test
    void bioluminescenceOnlyWhenEnabled() {
        CaveSystemConfig cfgOff = new CaveSystemConfig(TEST_SEED,
                200f, 100f, 10, 12, 0.1f, false, false, false);
        GeneratedCaveSystem resultOff = generator.generate(cfgOff);
        assertTrue(resultOff.bioluminescentPatches.isEmpty(),
                "No glow patches when bioluminescence is disabled");

        // When enabled, over multiple seeds we should see patches.
        int totalPatches = 0;
        for (long seed = 0; seed < 20; seed++) {
            CaveSystemConfig cfgOn = new CaveSystemConfig(seed,
                    200f, 100f, 10, 12, 0.1f, false, false, true);
            GeneratedCaveSystem resultOn = generator.generate(cfgOn);
            totalPatches += resultOn.bioluminescentPatches.size();
        }
        assertTrue(totalPatches > 0,
                "Should have some glow patches when bioluminescence is enabled");
    }

    @Test
    void veinMineralsAreValidCommodities() {
        Set<String> validMinerals = new HashSet<>(Arrays.asList(
                "iron_ore", "copper", "silicon", "carbon", "dark_crystals"
        ));

        for (long seed = 0; seed < 50; seed++) {
            CaveSystemConfig cfg = new CaveSystemConfig(seed,
                    200f, 100f, 8, 10, 0.1f, false, false, false);
            GeneratedCaveSystem result = generator.generate(cfg);
            for (CaveVein vein : result.veins) {
                assertTrue(validMinerals.contains(vein.mineral),
                        "Unknown mineral: " + vein.mineral + ", seed=" + seed);
                assertTrue(vein.richness >= 0.2f && vein.richness <= 1.0f,
                        "Richness out of range: " + vein.richness);
                assertTrue(vein.tStart >= 0f && vein.tStart <= 1f,
                        "tStart out of range: " + vein.tStart);
                assertTrue(vein.tEnd >= vein.tStart && vein.tEnd <= 1f,
                        "tEnd out of range: " + vein.tEnd);
                assertTrue(vein.widthM >= 0.1f && vein.widthM <= 0.8f,
                        "Width out of range: " + vein.widthM);
            }
        }
    }

    /** Find the chamber index whose centre matches the given coordinates. */
    private int findChamber(List<CaveChamber> chambers, float x, float y, float z) {
        for (int i = 0; i < chambers.size(); i++) {
            CaveChamber c = chambers.get(i);
            if (Math.abs(c.cx - x) < 0.01f && Math.abs(c.cy - y) < 0.01f
                    && Math.abs(c.cz - z) < 0.01f) {
                return i;
            }
        }
        return -1;
    }
}
