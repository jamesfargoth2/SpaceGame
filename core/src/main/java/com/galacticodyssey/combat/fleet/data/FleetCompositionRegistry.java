package com.galacticodyssey.combat.fleet.data;

import com.galacticodyssey.galaxy.faction.FactionEthos;
import java.util.EnumMap;
import java.util.Map;

public final class FleetCompositionRegistry {
    private final Map<FactionEthos, FleetCompositionData> compositions = new EnumMap<>(FactionEthos.class);

    public void registerDefaults() {
        register(FactionEthos.MILITARIST, militarist());
        register(FactionEthos.CORPORATE, corporate());
        register(FactionEthos.FEDERATION, federation());
        register(FactionEthos.PIRATE_SYNDICATE, pirate());
        register(FactionEthos.ISOLATIONIST, isolationist());
    }

    public void register(FactionEthos ethos, FleetCompositionData data) {
        compositions.put(ethos, data);
    }

    public FleetCompositionData getForEthos(FactionEthos ethos) {
        return compositions.get(ethos);
    }

    private static FleetCompositionData militarist() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "militarist_battle_fleet";
        d.factionEthos = "MILITARIST";
        d.doctrineDefault = FleetDoctrine.AGGRESSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DREADNOUGHT, 0, 1));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BATTLESHIP, 1, 3));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 2, 5));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 12, 24));
        return d;
    }

    private static FleetCompositionData corporate() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "corporate_fleet";
        d.factionEthos = "CORPORATE";
        d.doctrineDefault = FleetDoctrine.BALANCED;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BATTLESHIP, 0, 2));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 3, 5));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 5, 10));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 8, 16));
        return d;
    }

    private static FleetCompositionData federation() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "federation_fleet";
        d.factionEthos = "FEDERATION";
        d.doctrineDefault = FleetDoctrine.DEFENSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CARRIER, 0, 1));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 6, 10));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 10, 20));
        return d;
    }

    private static FleetCompositionData pirate() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "pirate_raider_pack";
        d.factionEthos = "PIRATE_SYNDICATE";
        d.doctrineDefault = FleetDoctrine.EVASIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 2, 4));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 6, 12));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 8, 16));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BOMBER, 2, 4));
        return d;
    }

    private static FleetCompositionData isolationist() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "isolationist_picket";
        d.factionEthos = "ISOLATIONIST";
        d.doctrineDefault = FleetDoctrine.DEFENSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 1, 3));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 10, 20));
        return d;
    }
}
