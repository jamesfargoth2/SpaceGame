package com.galacticodyssey.shipbuilder;

import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.ship.ShipSizeClass;

import java.util.EnumMap;
import java.util.Map;

public class BuildCostCalculator {
    private final Map<RoomType, RoomCost> roomCosts = new EnumMap<>(RoomType.class);
    private final Map<ShipSizeClass, Integer> hullCostPerMeter = new EnumMap<>(ShipSizeClass.class);
    private int installFee = 500;
    private float refundRate = 0.7f;

    public static class RoomCost {
        public final int base;
        public final int perM3;
        public RoomCost(int base, int perM3) { this.base = base; this.perM3 = perM3; }
    }

    public BuildCostCalculator() {
        roomCosts.put(RoomType.COCKPIT, new RoomCost(5000, 200));
        roomCosts.put(RoomType.ENGINE_ROOM, new RoomCost(8000, 300));
        roomCosts.put(RoomType.CREW_QUARTERS, new RoomCost(3000, 150));
        roomCosts.put(RoomType.MEDBAY, new RoomCost(6000, 250));
        roomCosts.put(RoomType.ARMORY, new RoomCost(7000, 280));
        roomCosts.put(RoomType.CARGO_BAY, new RoomCost(2000, 100));

        hullCostPerMeter.put(ShipSizeClass.SMALL, 1000);
        hullCostPerMeter.put(ShipSizeClass.MEDIUM, 2500);
        hullCostPerMeter.put(ShipSizeClass.LARGE, 5000);
    }

    public int roomConstructionCost(RoomDesign room) {
        RoomCost cost = roomCosts.get(room.type);
        if (cost == null) return 0;
        return cost.base + cost.perM3 * room.volume();
    }

    public int roomRefund(RoomDesign room) {
        return (int) (roomConstructionCost(room) * refundRate);
    }

    public int hullCost(ShipSizeClass sizeClass, float spineLength) {
        Integer costPerMeter = hullCostPerMeter.get(sizeClass);
        if (costPerMeter == null) return 0;
        return (int) (costPerMeter * spineLength);
    }

    public int moduleInstallFee() {
        return installFee;
    }

    public int totalDesignCost(ShipDesign design) {
        int total = hullCost(design.sizeClass, design.hull.estimateSpineLength());
        for (RoomDesign room : design.rooms) {
            total += roomConstructionCost(room);
        }
        total += design.modules.size() * installFee;
        return total;
    }
}
