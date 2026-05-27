package com.galacticodyssey.galaxy;

import java.util.*;

public final class FactionTerritoryGenerator {

    public List<FactionTerritory> generate(long galaxySeed, List<FactionSeed> factions, int systemCount) {
        long factionSeed = SeedDeriver.domain(galaxySeed, SeedDeriver.FACTION_DOMAIN);
        Random rng = new Random(factionSeed);

        double[][] systemPositions = new double[systemCount][2];
        long[] systemIds = new long[systemCount];
        for (int i = 0; i < systemCount; i++) {
            systemPositions[i][0] = (rng.nextDouble() - 0.5) * 400.0;
            systemPositions[i][1] = (rng.nextDouble() - 0.5) * 400.0;
            systemIds[i] = SeedDeriver.forId(factionSeed, i);
        }

        int[] ownership = new int[systemCount];
        Arrays.fill(ownership, -1);

        for (int i = 0; i < systemCount; i++) {
            float bestScore = Float.MAX_VALUE;
            int bestFaction = -1;
            for (int f = 0; f < factions.size(); f++) {
                FactionSeed fs = factions.get(f);
                double dx = systemPositions[i][0] - fs.startX;
                double dy = systemPositions[i][1] - fs.startY;
                double dist = Math.sqrt(dx * dx + dy * dy);
                float score = (float) (dist / fs.strength);
                if (score < bestScore) {
                    bestScore = score;
                    bestFaction = f;
                }
            }
            ownership[i] = bestFaction;
        }

        List<FactionTerritory> territories = new ArrayList<>();
        for (int f = 0; f < factions.size(); f++) {
            FactionSeed fs = factions.get(f);
            List<Long> controlled = new ArrayList<>();
            List<Long> border = new ArrayList<>();

            for (int i = 0; i < systemCount; i++) {
                if (ownership[i] == f) {
                    controlled.add(systemIds[i]);
                    if (isBorderSystem(i, f, systemPositions, ownership, systemCount)) {
                        border.add(systemIds[i]);
                    }
                }
            }

            double biasX = 0, biasY = 0;
            for (Long sysId : controlled) {
                int idx = findSystemIndex(systemIds, sysId);
                if (idx >= 0) {
                    biasX += systemPositions[idx][0] - fs.startX;
                    biasY += systemPositions[idx][1] - fs.startY;
                }
            }
            if (!controlled.isEmpty()) {
                biasX /= controlled.size();
                biasY /= controlled.size();
            }

            float influence = (float) controlled.size() / systemCount;

            territories.add(new FactionTerritory(fs.factionId, fs.startX, fs.startY,
                controlled, border, influence, biasX, biasY));
        }

        return territories;
    }

    private boolean isBorderSystem(int sysIdx, int faction, double[][] positions, int[] ownership, int total) {
        double threshold = 30.0;
        for (int j = 0; j < total; j++) {
            if (j == sysIdx || ownership[j] == faction) continue;
            double dx = positions[sysIdx][0] - positions[j][0];
            double dy = positions[sysIdx][1] - positions[j][1];
            if (dx * dx + dy * dy < threshold * threshold) {
                return true;
            }
        }
        return false;
    }

    private int findSystemIndex(long[] systemIds, long id) {
        for (int i = 0; i < systemIds.length; i++) {
            if (systemIds[i] == id) return i;
        }
        return -1;
    }
}
