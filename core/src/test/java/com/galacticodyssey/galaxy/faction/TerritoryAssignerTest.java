package com.galacticodyssey.galaxy.faction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryAssignerTest {

    @Test
    void starsAssignedToNearestFaction() {
        // Two factions at known positions
        FactionData factionA = makeFaction("a", 0, 0, 0, 1.0f, 500f);
        FactionData factionB = makeFaction("b", 400, 0, 0, 1.0f, 500f);

        // Star near faction A
        long[] ids = {1L, 2L};
        double[] xs = {10.0, 390.0};
        double[] ys = {0.0, 0.0};
        double[] zs = {0.0, 0.0};

        TerritoryAssigner assigner = new TerritoryAssigner();
        Map<Long, String> result = assigner.assign(List.of(factionA, factionB), ids, xs, ys, zs);

        assertEquals("a", result.get(1L), "Star near faction A should be assigned to A");
        assertEquals("b", result.get(2L), "Star near faction B should be assigned to B");
    }

    @Test
    void unclaimedStarsOutsideInfluence() {
        FactionData faction = makeFaction("a", 0, 0, 0, 1.0f, 100f);

        long[] ids = {1L, 2L};
        double[] xs = {50.0, 500.0};  // star 2 is way outside influence
        double[] ys = {0.0, 0.0};
        double[] zs = {0.0, 0.0};

        TerritoryAssigner assigner = new TerritoryAssigner();
        Map<Long, String> result = assigner.assign(List.of(faction), ids, xs, ys, zs);

        assertTrue(result.containsKey(1L), "Star within influence should be assigned");
        assertFalse(result.containsKey(2L), "Star outside influence should be unclaimed");
    }

    @Test
    void contestedSystemsDetected() {
        FactionData factionA = makeFaction("a", 0, 0, 0, 1.0f, 500f);
        FactionData factionB = makeFaction("b", 100, 0, 0, 1.0f, 500f);

        // Stars: one deep in A territory, one on the border, one deep in B territory
        long[] ids = {1L, 2L, 3L};
        double[] xs = {0.0, 50.0, 100.0};
        double[] ys = {0.0, 0.0, 0.0};
        double[] zs = {0.0, 0.0, 0.0};

        TerritoryAssigner assigner = new TerritoryAssigner();
        Map<Long, String> assignment = assigner.assign(List.of(factionA, factionB), ids, xs, ys, zs);

        // Star 2 at x=50 is the midpoint -- should be near border
        // With threshold of 60 LY, stars 1, 2, 3 should all be contested since
        // they each have a neighbour owned by a different faction within 60 LY
        List<Long> contested = assigner.findContestedSystems(
                assignment, ids, xs, ys, zs, 60f);

        assertTrue(contested.contains(2L),
                "Border star should be contested");
    }

    @Test
    void militaryStrengthWeightsAssignment() {
        // Faction A is weak, faction B is strong, both equidistant from the star
        FactionData factionA = makeFaction("a", -100, 0, 0, 0.3f, 500f);
        FactionData factionB = makeFaction("b", 100, 0, 0, 1.0f, 500f);

        // Star at origin -- equidistant from both (100 LY each)
        long[] ids = {1L};
        double[] xs = {0.0};
        double[] ys = {0.0};
        double[] zs = {0.0};

        TerritoryAssigner assigner = new TerritoryAssigner();
        Map<Long, String> result = assigner.assign(List.of(factionA, factionB), ids, xs, ys, zs);

        // B has higher military, so weighted distance = 100/1.0 = 100
        // A has lower military, so weighted distance = 100/0.3 = 333
        // Star should go to B
        assertEquals("b", result.get(1L),
                "Equidistant star should go to the stronger faction");
    }

    private FactionData makeFaction(String id, double x, double y, double z,
                                     float military, float influence) {
        return new FactionData(id, "Faction " + id, x, y, z,
                military, 0.5f, FactionEthos.FEDERATION,
                0.5f, 0.5f, 0.5f, influence, "HUMAN_COLONY");
    }
}
