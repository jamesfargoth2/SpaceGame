package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipSubsystemsSnapshot;

import java.util.EnumMap;
import java.util.Map;

/**
 * Functional health of a ship's targetable subsystems. Distinct from
 * {@link com.galacticodyssey.ship.structure.StructuralIntegrityComponent}, which models
 * physical hull zones/pressure — this models whether a system still works.
 */
public class ShipSubsystemsComponent implements Component, Snapshotable<ShipSubsystemsSnapshot> {

    public enum SubsystemType { ENGINES, SHIELDS, WEAPONS, LIFE_SUPPORT }

    public static final class Subsystem {
        public float health;
        public float maxHealth;
        /** Seconds of remaining EMP soft-disable. > 0 means temporarily offline. */
        public float empDisableTimer;
        public boolean destroyed;
    }

    public final EnumMap<SubsystemType, Subsystem> subsystems = new EnumMap<>(SubsystemType.class);

    /** Populate all subsystems at full health. Call once when a ship is built. */
    public void initDefaults(float healthPerSubsystem) {
        for (SubsystemType type : SubsystemType.values()) {
            Subsystem s = new Subsystem();
            s.health = healthPerSubsystem;
            s.maxHealth = healthPerSubsystem;
            s.empDisableTimer = 0f;
            s.destroyed = false;
            subsystems.put(type, s);
        }
    }

    public Subsystem get(SubsystemType type) {
        return subsystems.get(type);
    }

    public boolean enginesOperational() {
        Subsystem engines = subsystems.get(SubsystemType.ENGINES);
        return engines != null && engines.health > 0f && engines.empDisableTimer <= 0f;
    }

    @Override
    public ShipSubsystemsSnapshot takeSnapshot() {
        ShipSubsystemsSnapshot snap = new ShipSubsystemsSnapshot();
        for (Map.Entry<SubsystemType, Subsystem> e : subsystems.entrySet()) {
            ShipSubsystemsSnapshot.Entry se = new ShipSubsystemsSnapshot.Entry();
            se.type = e.getKey().name();
            se.health = e.getValue().health;
            se.maxHealth = e.getValue().maxHealth;
            se.empDisableTimer = e.getValue().empDisableTimer;
            se.destroyed = e.getValue().destroyed;
            snap.entries.add(se);
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(ShipSubsystemsSnapshot snap) {
        subsystems.clear();
        for (ShipSubsystemsSnapshot.Entry se : snap.entries) {
            Subsystem s = new Subsystem();
            s.health = se.health;
            s.maxHealth = se.maxHealth;
            s.empDisableTimer = se.empDisableTimer;
            s.destroyed = se.destroyed;
            subsystems.put(SubsystemType.valueOf(se.type), s);
        }
    }
}
