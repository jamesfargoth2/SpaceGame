package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import java.util.*;

public class AdjacencyBonusCalculator {

    public static class LayoutBonus {
        public final String stat;
        public final float bonus;
        public final String label;
        public LayoutBonus(String stat, float bonus, String label) {
            this.stat = stat;
            this.bonus = bonus;
            this.label = label;
        }
    }

    private final ShipDesignValidator validator = new ShipDesignValidator();

    public List<LayoutBonus> computeBonuses(ShipDesign design) {
        List<LayoutBonus> bonuses = new ArrayList<>();
        computeAdjacencyBonuses(design, bonuses);
        computePositionalBonuses(design, bonuses);
        return bonuses;
    }

    private void computeAdjacencyBonuses(ShipDesign design, List<LayoutBonus> bonuses) {
        for (RoomDesign a : design.rooms) {
            for (RoomDesign b : design.rooms) {
                if (a == b) continue;
                if (a.type == RoomType.MEDBAY && b.type == RoomType.CREW_QUARTERS
                    && validator.areAdjacent(a, b)) {
                    bonuses.add(new LayoutBonus("healingRate", 0.15f, "+15% Healing"));
                    return;
                }
            }
        }
        for (RoomDesign a : design.rooms) {
            for (RoomDesign b : design.rooms) {
                if (a == b) continue;
                if (a.type == RoomType.ARMORY && b.type == RoomType.CARGO_BAY
                    && validator.areAdjacent(a, b)) {
                    bonuses.add(new LayoutBonus("reloadSpeed", 0.10f, "+10% Reload"));
                    return;
                }
            }
        }
    }

    private void computePositionalBonuses(ShipDesign design, List<LayoutBonus> bonuses) {
        if (design.rooms.isEmpty()) return;
        int maxGridX = 0;
        for (RoomDesign r : design.rooms) {
            maxGridX = Math.max(maxGridX, r.gridX + r.sizeX);
        }
        for (RoomDesign room : design.rooms) {
            float normalizedPosition = (float) room.gridX / Math.max(maxGridX, 1);
            if (room.type == RoomType.ENGINE_ROOM && normalizedPosition > 0.6f) {
                bonuses.add(new LayoutBonus("fuelEfficiency", 0.05f, "+5% Fuel Eff."));
            }
            if (room.type == RoomType.COCKPIT && normalizedPosition < 0.3f) {
                bonuses.add(new LayoutBonus("sensorRange", 0.10f, "+10% Sensors"));
            }
        }
    }
}
