package com.galacticodyssey.ship.flooding;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.ship.flooding.components.BreachRepairableComponent;
import com.galacticodyssey.ship.flooding.components.ShipFloodingComponent;
import com.galacticodyssey.water.Compartment;

/**
 * Factory that configures a ship entity's flooding components with
 * a standard 3-compartment layout: engine room, cargo hold, and
 * crew quarters.
 *
 * <p>This matches the typical cargo ship layout where a torpedo
 * strike to the engine room will cause water to pour through
 * internal doorways into the adjacent cargo hold and eventually
 * the crew quarters.
 *
 * <p>Compartment connectivity:
 * <pre>
 *   [Engine Room] --doorway-- [Cargo Hold] --doorway-- [Crew Quarters]
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   Entity ship = shipFactory.createShip(seed, sizeClass, x, y, z);
 *   FloodableShipFactory.attachFloodingLayout(ship);
 *   // Later, when torpedo hits:
 *   eventBus.publish(new HullBreachEvent(ship, "engine_room", 0.5f, 2.0f));
 * </pre>
 */
public final class FloodableShipFactory {

    /** Compartment ID constants for the standard 3-compartment layout. */
    public static final String ENGINE_ROOM = "engine_room";
    public static final String CARGO_HOLD = "cargo_hold";
    public static final String CREW_QUARTERS = "crew_quarters";

    // Default compartment volumes in m^3 (representative for a medium cargo ship)
    private static final float ENGINE_ROOM_VOLUME = 120f;   // ~5m x 8m x 3m
    private static final float CARGO_HOLD_VOLUME = 200f;    // ~8m x 8m x 3m
    private static final float CREW_QUARTERS_VOLUME = 80f;  // ~5m x 5m x 3m

    // Default compartment heights in metres
    private static final float DEFAULT_COMPARTMENT_HEIGHT = 3.0f;

    // Default doorway parameters
    private static final float DOORWAY_AREA = 1.6f;  // 0.8m x 2.0m standard door
    private static final float DOORWAY_CD = 0.4f;    // partially obstructed passage

    private FloodableShipFactory() {} // utility class

    /**
     * Attaches a {@link ShipFloodingComponent} with the standard 3-compartment
     * layout to an existing ship entity. Also attaches a
     * {@link BreachRepairableComponent} so the player can seal breaches.
     *
     * <p>Compartment centroids are placed along the ship's local X axis
     * (stern to bow), offset in Y to model the fact that compartments
     * are below the waterline on different sides -- this asymmetry
     * drives the listing behaviour when only one compartment floods.
     *
     * @param shipEntity the ship entity to configure
     * @return the configured ShipFloodingComponent for further customization
     */
    public static ShipFloodingComponent attachFloodingLayout(Entity shipEntity) {
        return attachFloodingLayout(shipEntity,
                ENGINE_ROOM_VOLUME, CARGO_HOLD_VOLUME, CREW_QUARTERS_VOLUME,
                DEFAULT_COMPARTMENT_HEIGHT);
    }

    /**
     * Attaches a flooding layout with custom compartment volumes.
     *
     * @param shipEntity       the ship entity
     * @param engineRoomVolume engine room volume in m^3
     * @param cargoHoldVolume  cargo hold volume in m^3
     * @param crewQuartersVolume crew quarters volume in m^3
     * @param compartmentHeight uniform compartment height in metres
     * @return the configured ShipFloodingComponent
     */
    public static ShipFloodingComponent attachFloodingLayout(
            Entity shipEntity,
            float engineRoomVolume, float cargoHoldVolume, float crewQuartersVolume,
            float compartmentHeight) {

        ShipFloodingComponent flooding = new ShipFloodingComponent();
        flooding.compartmentHeight = compartmentHeight;

        // --- Create compartments ---

        Compartment engineRoom = new Compartment(ENGINE_ROOM, engineRoomVolume);
        // Engine room at the stern, offset to port side (negative X)
        // to create asymmetric listing when this compartment floods first
        engineRoom.centroid.set(-8f, -1.0f, -2f);
        engineRoom.connectedTo.add(CARGO_HOLD);

        Compartment cargoHold = new Compartment(CARGO_HOLD, cargoHoldVolume);
        // Cargo hold in the centre, roughly centred
        cargoHold.centroid.set(0f, -1.0f, 0f);
        cargoHold.connectedTo.add(ENGINE_ROOM);
        cargoHold.connectedTo.add(CREW_QUARTERS);

        Compartment crewQuarters = new Compartment(CREW_QUARTERS, crewQuartersVolume);
        // Crew quarters at the bow, offset to starboard (positive X)
        crewQuarters.centroid.set(8f, -0.5f, 2f);
        crewQuarters.connectedTo.add(CARGO_HOLD);

        flooding.compartments.add(engineRoom);
        flooding.compartments.add(cargoHold);
        flooding.compartments.add(crewQuarters);

        // --- Create doorway connections ---

        // Engine room <-> Cargo hold: standard internal doorway
        DoorwayConnection engineToCargo = new DoorwayConnection(
                ENGINE_ROOM, CARGO_HOLD, DOORWAY_AREA, DOORWAY_CD);
        engineToCargo.sealable = true;

        // Cargo hold <-> Crew quarters: standard internal doorway
        DoorwayConnection cargoToCrew = new DoorwayConnection(
                CARGO_HOLD, CREW_QUARTERS, DOORWAY_AREA, DOORWAY_CD);
        cargoToCrew.sealable = true;

        flooding.doorways.add(engineToCargo);
        flooding.doorways.add(cargoToCrew);

        // --- Attach to entity ---
        shipEntity.add(flooding);
        shipEntity.add(new BreachRepairableComponent());

        return flooding;
    }

    /**
     * Creates a torpedo impact breach in the engine room of the given
     * ship entity. This is a convenience method that directly sets the
     * breach parameters on the engine room compartment.
     *
     * @param shipEntity the ship entity with a ShipFloodingComponent
     * @param breachArea area of the breach in m^2 (torpedo: ~0.3-0.8 m^2)
     * @param breachDepth depth of breach below pressure boundary in metres
     */
    public static void applyTorpedoBreach(Entity shipEntity,
                                           float breachArea, float breachDepth) {
        ShipFloodingComponent flooding = shipEntity.getComponent(ShipFloodingComponent.class);
        if (flooding == null) return;

        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            if (ENGINE_ROOM.equals(comp.id)) {
                comp.breachArea = breachArea;
                comp.breachDepth = breachDepth;
                comp.sealed = false;
                return;
            }
        }
    }

    /**
     * Seals the breach in a specific compartment. Water already inside
     * remains, but no further external ingress occurs.
     *
     * @param shipEntity    the ship entity
     * @param compartmentId the compartment to seal
     * @return true if the compartment was found and sealed
     */
    public static boolean sealBreach(Entity shipEntity, String compartmentId) {
        ShipFloodingComponent flooding = shipEntity.getComponent(ShipFloodingComponent.class);
        if (flooding == null) return false;

        for (int i = 0; i < flooding.compartments.size; i++) {
            Compartment comp = flooding.compartments.get(i);
            if (comp.id.equals(compartmentId)) {
                comp.breachArea = 0f;
                comp.sealed = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Seals a doorway between two compartments, preventing further
     * cross-flow. This is a damage control action the player can take.
     *
     * @param shipEntity    the ship entity
     * @param compartmentA  one end of the doorway
     * @param compartmentB  other end of the doorway
     * @return true if the doorway was found and sealed
     */
    public static boolean sealDoorway(Entity shipEntity,
                                       String compartmentA, String compartmentB) {
        ShipFloodingComponent flooding = shipEntity.getComponent(ShipFloodingComponent.class);
        if (flooding == null) return false;

        for (int i = 0; i < flooding.doorways.size; i++) {
            DoorwayConnection door = flooding.doorways.get(i);
            if ((door.compartmentA.equals(compartmentA) && door.compartmentB.equals(compartmentB))
                    || (door.compartmentA.equals(compartmentB) && door.compartmentB.equals(compartmentA))) {
                door.sealed = true;
                return true;
            }
        }
        return false;
    }
}
