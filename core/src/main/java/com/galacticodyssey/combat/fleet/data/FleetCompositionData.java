package com.galacticodyssey.combat.fleet.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FleetCompositionData {
    public String id;
    public String factionEthos;
    public FleetDoctrine doctrineDefault;
    public final List<SlotRange> slots = new ArrayList<>();

    public static final class SlotRange {
        public FleetShipClass shipClass;
        public int minCount;
        public int maxCount;

        public SlotRange() {}

        public SlotRange(FleetShipClass shipClass, int minCount, int maxCount) {
            this.shipClass = shipClass;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }

    public List<FleetShipEntry> generate(float militaryStrength, Random rng) {
        List<FleetShipEntry> result = new ArrayList<>();
        for (SlotRange slot : slots) {
            int range = slot.maxCount - slot.minCount;
            int count = slot.minCount + Math.round(range * militaryStrength);
            count = Math.max(slot.minCount, Math.min(slot.maxCount,
                count + rng.nextInt(3) - 1));
            if (count > 0) {
                result.add(new FleetShipEntry(slot.shipClass, count, 1.0f));
            }
        }
        return result;
    }
}
