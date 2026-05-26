package com.galacticodyssey.planet.cave;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.*;

/**
 * Procedurally generates underground cave systems with chambers,
 * Bezier tunnels, mineral veins, and bioluminescent patches.
 * Pure data generator -- no libGDX dependencies.
 */
public final class CaveSystemGenerator {

    private static final String[] MINERAL_PALETTE = {
        "iron_ore", "copper", "silicon", "carbon", "dark_crystals"
    };

    /** Bioluminescent colour presets: cyan, green, violet, amber. */
    private static final float[][] GLOW_COLORS = {
        {0.0f, 0.9f, 1.0f},   // cyan
        {0.2f, 1.0f, 0.3f},   // green
        {0.6f, 0.1f, 1.0f},   // violet
        {1.0f, 0.75f, 0.1f},  // amber
    };

    /** Cumulative probability thresholds for chamber type selection.
     *  10% crystal, 15% collapsed, 15% geothermal, 15% resource, 10% ancient, 35% generic. */
    private static final float[] TYPE_CDF = {0.10f, 0.25f, 0.40f, 0.55f, 0.65f, 1.0f};
    private static final ChamberType[] TYPE_VALUES = {
        ChamberType.CRYSTAL_POCKET,
        ChamberType.COLLAPSED,
        ChamberType.GEOTHERMAL_VENT,
        ChamberType.RESOURCE_DEPOSIT,
        ChamberType.ANCIENT_CHAMBER,
        ChamberType.GENERIC
    };

    public CaveSystemGenerator() {}

    /** Generate a cave system from the given configuration. */
    public GeneratedCaveSystem generate(CaveSystemConfig cfg) {
        long domainSeed = SeedDeriver.domain(cfg.seed, SeedDeriver.INTERIOR_DOMAIN);
        Random rng = new Random(domainSeed);

        List<CaveChamber> chambers = placeChambers(cfg, rng);
        List<BezierTunnel> tunnels = connectChambers(chambers, cfg, rng);
        List<CaveVein> veins = placeVeins(tunnels, rng);
        List<BioluminescentPatch> patches = placeBioluminescence(chambers, cfg, rng);

        return new GeneratedCaveSystem(chambers, tunnels, veins, patches);
    }

    // -- Step 1: Place chambers ------------------------------------------------

    private List<CaveChamber> placeChambers(CaveSystemConfig cfg, Random rng) {
        List<CaveChamber> chambers = new ArrayList<>();

        // Entry chamber near the surface.
        chambers.add(new CaveChamber(0f, -10f, 0f,
                RngUtil.range(rng, 3f, 8f),
                ChamberType.ENTRY, false, false));

        for (int i = 1; i < cfg.chamberCount; i++) {
            float angle = rng.nextFloat() * (float)(Math.PI * 2);
            float dist = RngUtil.range(rng, 5f, cfg.systemRadiusM);
            float cx = dist * (float) Math.cos(angle);
            float cz = dist * (float) Math.sin(angle);
            float cy = -RngUtil.range(rng, 15f, cfg.systemDepthM);
            float radius = RngUtil.range(rng, 3f, 15f);

            ChamberType type = rollChamberType(rng);

            boolean hasWater = cfg.hasUndergroundLake && rng.nextFloat() < 0.25f;
            boolean hasLava = cfg.hasLavaFlows && rng.nextFloat() < 0.15f;
            // Geothermal vents more likely to have lava.
            if (type == ChamberType.GEOTHERMAL_VENT && cfg.hasLavaFlows) {
                hasLava = rng.nextFloat() < 0.6f;
            }

            chambers.add(new CaveChamber(cx, cy, cz, radius, type, hasWater, hasLava));
        }
        return chambers;
    }

    private ChamberType rollChamberType(Random rng) {
        float roll = rng.nextFloat();
        for (int i = 0; i < TYPE_CDF.length; i++) {
            if (roll < TYPE_CDF[i]) {
                return TYPE_VALUES[i];
            }
        }
        return ChamberType.GENERIC;
    }

    // -- Step 2: Connect with Bezier tunnels (MST + extra loops) ---------------

    private List<BezierTunnel> connectChambers(
            List<CaveChamber> chambers, CaveSystemConfig cfg, Random rng) {

        int n = chambers.size();
        if (n < 2) return new ArrayList<>();

        // Prim's MST.
        boolean[] inTree = new boolean[n];
        float[] minCost = new float[n];
        int[] minEdge = new int[n];
        Arrays.fill(minCost, Float.MAX_VALUE);
        minCost[0] = 0f;
        Arrays.fill(minEdge, -1);

        List<int[]> mstEdges = new ArrayList<>();

        for (int iter = 0; iter < n; iter++) {
            // Pick cheapest node not yet in tree.
            int u = -1;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!inTree[i] && minCost[i] < best) {
                    best = minCost[i];
                    u = i;
                }
            }
            if (u == -1) break;
            inTree[u] = true;
            if (minEdge[u] >= 0) {
                mstEdges.add(new int[]{minEdge[u], u});
            }
            // Update neighbours.
            for (int v = 0; v < n; v++) {
                if (!inTree[v]) {
                    float d = dist(chambers.get(u), chambers.get(v));
                    if (d < minCost[v]) {
                        minCost[v] = d;
                        minEdge[v] = u;
                    }
                }
            }
        }

        // Build MST tunnels.
        List<BezierTunnel> tunnels = new ArrayList<>();
        Set<Long> usedEdges = new HashSet<>();
        for (int[] edge : mstEdges) {
            tunnels.add(makeTunnel(chambers.get(edge[0]), chambers.get(edge[1]), cfg, rng));
            usedEdges.add(edgeKey(edge[0], edge[1]));
        }

        // Add 1-3 extra tunnels for loops.
        int extraCount = RngUtil.range(rng, 1, 4);
        int attempts = 0;
        int added = 0;
        while (added < extraCount && attempts < extraCount * 10) {
            attempts++;
            int a = rng.nextInt(n);
            int b = rng.nextInt(n);
            if (a == b) continue;
            long key = edgeKey(a, b);
            if (usedEdges.contains(key)) continue;
            usedEdges.add(key);
            tunnels.add(makeTunnel(chambers.get(a), chambers.get(b), cfg, rng));
            added++;
        }

        return tunnels;
    }

    private long edgeKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | hi;
    }

    private float dist(CaveChamber a, CaveChamber b) {
        float dx = a.cx - b.cx;
        float dy = a.cy - b.cy;
        float dz = a.cz - b.cz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private BezierTunnel makeTunnel(CaveChamber from, CaveChamber to,
                                    CaveSystemConfig cfg, Random rng) {
        float dx = to.cx - from.cx;
        float dy = to.cy - from.cy;
        float dz = to.cz - from.cz;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Build a perpendicular offset direction.
        // Choose an arbitrary axis not parallel to (dx,dy,dz).
        float ax, ay, az;
        if (Math.abs(dx) < Math.abs(dy)) {
            ax = 1f; ay = 0f; az = 0f;
        } else {
            ax = 0f; ay = 1f; az = 0f;
        }
        // Cross product to get perpendicular.
        float px = dy * az - dz * ay;
        float py = dz * ax - dx * az;
        float pz = dx * ay - dy * ax;
        float pLen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pLen > 1e-6f) { px /= pLen; py /= pLen; pz /= pLen; }

        // Control point offsets: 10-40% of length, perpendicular.
        float offset1 = RngUtil.range(rng, 0.1f, 0.4f) * length * (rng.nextBoolean() ? 1f : -1f);
        float offset2 = RngUtil.range(rng, 0.1f, 0.4f) * length * (rng.nextBoolean() ? 1f : -1f);

        float ctrl1X = from.cx + dx * 0.33f + px * offset1;
        float ctrl1Y = from.cy + dy * 0.33f + py * offset1;
        float ctrl1Z = from.cz + dz * 0.33f + pz * offset1;

        float ctrl2X = from.cx + dx * 0.66f + px * offset2;
        float ctrl2Y = from.cy + dy * 0.66f + py * offset2;
        float ctrl2Z = from.cz + dz * 0.66f + pz * offset2;

        float startR = RngUtil.range(rng, 1.5f, 5f);
        float endR = RngUtil.range(rng, 1.5f, 5f);
        boolean collapsed = rng.nextFloat() < cfg.collapseChance;

        return new BezierTunnel(
                from.cx, from.cy, from.cz,
                to.cx, to.cy, to.cz,
                ctrl1X, ctrl1Y, ctrl1Z,
                ctrl2X, ctrl2Y, ctrl2Z,
                startR, endR, collapsed);
    }

    // -- Step 3: Place mineral veins -------------------------------------------

    private List<CaveVein> placeVeins(List<BezierTunnel> tunnels, Random rng) {
        List<CaveVein> veins = new ArrayList<>();
        for (BezierTunnel tunnel : tunnels) {
            if (rng.nextFloat() < 0.4f) {
                String mineral = MINERAL_PALETTE[rng.nextInt(MINERAL_PALETTE.length)];
                float richness = RngUtil.range(rng, 0.2f, 1.0f);
                float span = RngUtil.range(rng, 0.1f, 0.4f);
                float tStart = RngUtil.range(rng, 0.0f, 1.0f - span);
                float tEnd = tStart + span;
                float width = RngUtil.range(rng, 0.1f, 0.8f);
                veins.add(new CaveVein(mineral, richness, tStart, tEnd, width));
            }
        }
        return veins;
    }

    // -- Step 4: Bioluminescence -----------------------------------------------

    private List<BioluminescentPatch> placeBioluminescence(
            List<CaveChamber> chambers, CaveSystemConfig cfg, Random rng) {

        List<BioluminescentPatch> patches = new ArrayList<>();
        if (!cfg.hasBioluminescence) {
            return patches;
        }

        for (CaveChamber chamber : chambers) {
            if (rng.nextFloat() < 0.5f) {
                int count = RngUtil.range(rng, 1, 6);
                for (int i = 0; i < count; i++) {
                    float ox = RngUtil.range(rng, -chamber.radiusM * 0.8f, chamber.radiusM * 0.8f);
                    float oy = RngUtil.range(rng, -chamber.radiusM * 0.3f, chamber.radiusM * 0.3f);
                    float oz = RngUtil.range(rng, -chamber.radiusM * 0.8f, chamber.radiusM * 0.8f);
                    float radius = RngUtil.range(rng, 0.2f, 2.0f);
                    float[] color = GLOW_COLORS[rng.nextInt(GLOW_COLORS.length)];
                    float pulse = RngUtil.range(rng, 0.2f, 2.0f);
                    patches.add(new BioluminescentPatch(
                            chamber.cx + ox, chamber.cy + oy, chamber.cz + oz,
                            radius, color[0], color[1], color[2], pulse));
                }
            }
        }
        return patches;
    }
}
