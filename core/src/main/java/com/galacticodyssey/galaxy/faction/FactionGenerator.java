package com.galacticodyssey.galaxy.faction;

import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Procedural faction generator. Produces a list of unique {@link FactionData}
 * instances with distinct names, colours, ethos, and galaxy-space capitals.
 */
public final class FactionGenerator {

    private static final FactionEthos[] ALL_ETHOS = FactionEthos.values();

    /**
     * Pre-baked distinct colours to help differentiate factions on the galaxy map.
     * Cycled if more factions than colours are requested.
     */
    private static final float[][] MAP_COLORS = {
            {0.8f, 0.2f, 0.2f},   // red
            {0.2f, 0.6f, 0.9f},   // blue
            {0.2f, 0.8f, 0.3f},   // green
            {0.9f, 0.8f, 0.1f},   // yellow
            {0.7f, 0.2f, 0.9f},   // purple
            {0.9f, 0.5f, 0.1f},   // orange
            {0.1f, 0.8f, 0.8f},   // cyan
            {0.9f, 0.3f, 0.6f},   // pink
            {0.5f, 0.5f, 0.5f},   // grey
            {0.6f, 0.4f, 0.2f},   // brown
    };

    private final SpaceNameGenerator nameGen;

    public FactionGenerator() {
        this.nameGen = new SpaceNameGenerator();
    }

    public FactionGenerator(SpaceNameGenerator nameGen) {
        this.nameGen = nameGen;
    }

    /**
     * Generates the requested number of factions deterministically from the galaxy seed.
     */
    public List<FactionData> generateFactions(int count, long galaxySeed, Random rng) {
        List<FactionData> factions = new ArrayList<>(count);
        Set<String> usedNames = new HashSet<>();

        for (int i = 0; i < count; i++) {
            // Unique name
            String name;
            int nameAttempts = 0;
            do {
                name = nameGen.factionName(rng);
                nameAttempts++;
            } while (usedNames.contains(name) && nameAttempts < 50);
            usedNames.add(name);

            String id = "faction-" + i;

            // Ethos
            FactionEthos ethos = ALL_ETHOS[rng.nextInt(ALL_ETHOS.length)];

            // Strengths
            float military = RngUtil.range(rng, 0.3f, 1.0f);
            float economic = RngUtil.range(rng, 0.3f, 1.0f);

            // Influence radius
            float influence = RngUtil.range(rng, 200f, 800f);

            // Capital position (galaxy-space doubles, spread across a ~2000 LY cube)
            double cx = (rng.nextDouble() - 0.5) * 2000.0;
            double cy = (rng.nextDouble() - 0.5) * 2000.0;
            double cz = (rng.nextDouble() - 0.5) * 400.0;

            // Map colour
            float[] color = MAP_COLORS[i % MAP_COLORS.length];
            // Add slight random variation so duplicated-palette factions differ
            float cr = clamp(color[0] + RngUtil.range(rng, -0.05f, 0.05f), 0f, 1f);
            float cg = clamp(color[1] + RngUtil.range(rng, -0.05f, 0.05f), 0f, 1f);
            float cb = clamp(color[2] + RngUtil.range(rng, -0.05f, 0.05f), 0f, 1f);

            // Naming style from ethos
            String namingStyle = namingStyleForEthos(ethos);

            factions.add(new FactionData(id, name, cx, cy, cz,
                    military, economic, ethos, cr, cg, cb, influence, namingStyle));
        }
        return factions;
    }

    private static String namingStyleForEthos(FactionEthos ethos) {
        switch (ethos) {
            case CORPORATE:
            case MILITARIST:
                return "FACTION_IMPERIAL";
            case ISOLATIONIST:
            case PIRATE_SYNDICATE:
                return "ALIEN_HARSH";
            case FEDERATION:
                return "HUMAN_COLONY";
            default:
                return "HUMAN_COLONY";
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
