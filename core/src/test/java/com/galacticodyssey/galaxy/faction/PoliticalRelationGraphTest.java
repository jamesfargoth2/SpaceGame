package com.galacticodyssey.galaxy.faction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PoliticalRelationGraphTest {

    private static final long FIXED_SEED = 99L;

    @Test
    void deterministic() {
        List<FactionData> factions = makeTestFactions();
        PoliticalRelationGraph graph = new PoliticalRelationGraph();

        Map<String, Map<String, PoliticalRelation>> a = graph.generate(factions, new Random(FIXED_SEED));
        Map<String, Map<String, PoliticalRelation>> b = graph.generate(factions, new Random(FIXED_SEED));

        for (FactionData f : factions) {
            Map<String, PoliticalRelation> relA = a.get(f.id);
            Map<String, PoliticalRelation> relB = b.get(f.id);
            assertEquals(relA, relB,
                    "Relations for " + f.id + " should be deterministic");
        }
    }

    @Test
    void sameEthosMoreFriendly() {
        // Two factions with same ethos, far apart (so no proximity penalty)
        FactionData a = makeFaction("a", FactionEthos.FEDERATION, 0, 0, 0);
        FactionData b = makeFaction("b", FactionEthos.FEDERATION, 2000, 0, 0);
        // Two factions with different ethos, far apart
        FactionData c = makeFaction("c", FactionEthos.CORPORATE, 0, 2000, 0);

        // Test many seeds and count how often same-ethos pairs are friendlier
        int sameEthosFriendlier = 0;
        int total = 200;
        for (int i = 0; i < total; i++) {
            PoliticalRelationGraph graph = new PoliticalRelationGraph();
            Map<String, Map<String, PoliticalRelation>> rels =
                    graph.generate(List.of(a, b, c), new Random(FIXED_SEED + i));

            PoliticalRelation abRel = rels.get("a").get("b"); // same ethos
            PoliticalRelation acRel = rels.get("a").get("c"); // different ethos

            if (abRel.ordinal() < acRel.ordinal()) { // lower ordinal = friendlier
                sameEthosFriendlier++;
            }
        }

        assertTrue(sameEthosFriendlier > total * 0.5,
                "Same-ethos pairs should tend to be friendlier: " +
                        sameEthosFriendlier + "/" + total);
    }

    @Test
    void relationIsSymmetric() {
        List<FactionData> factions = makeTestFactions();
        PoliticalRelationGraph graph = new PoliticalRelationGraph();
        Map<String, Map<String, PoliticalRelation>> rels =
                graph.generate(factions, new Random(FIXED_SEED));

        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                String idA = factions.get(i).id;
                String idB = factions.get(j).id;
                assertEquals(rels.get(idA).get(idB), rels.get(idB).get(idA),
                        "Relation between " + idA + " and " + idB + " should be symmetric");
            }
        }
    }

    private List<FactionData> makeTestFactions() {
        return List.of(
                makeFaction("f0", FactionEthos.CORPORATE, 0, 0, 0),
                makeFaction("f1", FactionEthos.MILITARIST, 500, 0, 0),
                makeFaction("f2", FactionEthos.FEDERATION, 0, 500, 0),
                makeFaction("f3", FactionEthos.PIRATE_SYNDICATE, 500, 500, 0),
                makeFaction("f4", FactionEthos.ISOLATIONIST, 250, 250, 0)
        );
    }

    private FactionData makeFaction(String id, FactionEthos ethos,
                                     double x, double y, double z) {
        return new FactionData(id, "Faction " + id, x, y, z,
                0.5f, 0.5f, ethos,
                0.5f, 0.5f, 0.5f, 500f, "HUMAN_COLONY");
    }
}
