package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.ShipFactory;
import com.galacticodyssey.ship.ShipSizeClass;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Expands collapsed fleet entities into individual ship member entities when the player
 * enters {@value #EXPAND_RANGE} m of the fleet anchor, and collapses them back when the
 * player moves beyond {@value #COLLAPSE_RANGE} m.
 *
 * <p>On expansion each ship entry in {@link FleetComponent#composition} spawns one
 * {@link FleetMemberComponent} entity per count, with squadron and formation-slot indices
 * assigned sequentially (squadron size = 4).
 */
public class FleetExpansionSystem extends EntitySystem {
    public static final int PRIORITY = 3;
    private static final float EXPAND_RANGE = 50000f;
    private static final float COLLAPSE_RANGE = 60000f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetFormationComponent.class, FleetTacticsComponent.class
    ).get();

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class
    ).get();

    private final EventBus eventBus;
    private final Supplier<Vector3> playerPositionSupplier;
    private final FormationRegistry formationRegistry;
    private final ShipFactory shipFactory;
    private Engine engine;

    public FleetExpansionSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier,
                                FormationRegistry formationRegistry, ShipFactory shipFactory) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
        this.formationRegistry = formationRegistry;
        this.shipFactory = shipFactory;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        Vector3 playerPos = playerPositionSupplier.get();

        for (Entity fleetEntity : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(fleetEntity);
            FleetFormationComponent ffc = FORMATION_M.get(fleetEntity);
            float dist = playerPos.dst(ffc.localAnchorX, ffc.localAnchorY, ffc.localAnchorZ);

            if (!fc.expanded && dist < EXPAND_RANGE) {
                expandFleet(fleetEntity, fc, ffc);
            } else if (fc.expanded && dist > COLLAPSE_RANGE) {
                collapseFleet(fleetEntity, fc);
            }
        }
    }

    private void expandFleet(Entity fleetEntity, FleetComponent fc, FleetFormationComponent ffc) {
        FormationTemplate template = formationRegistry.get(ffc.formationTemplateId);
        int slotIndex = 0;
        int squadronIndex = 0;
        int inSquadron = 0;

        for (FleetShipEntry entry : fc.composition) {
            for (int i = 0; i < entry.count; i++) {
                String archetypeId = "veteran";
                // createNpcCombatShip builds the full flight/physics stack, adds a TransformComponent,
                // and registers the entity with the engine — so we only add the fleet membership here.
                Entity ship = shipFactory.createNpcCombatShip(
                    fc.fleetId.hashCode() * 31L + slotIndex,
                    mapToSizeClass(entry.shipClass),
                    archetypeId,
                    ffc.localAnchorX, ffc.localAnchorY, ffc.localAnchorZ);

                FleetMemberComponent fmc = new FleetMemberComponent();
                fmc.fleetEntity = fleetEntity;
                fmc.fleetId = fc.fleetId;
                fmc.squadronIndex = squadronIndex;
                fmc.role = entry.shipClass.defaultRole;
                fmc.formationSlotIndex = slotIndex;
                ship.add(fmc);
                // createNpcCombatShip already added the entity to the engine — do NOT add again.

                slotIndex++;
                inSquadron++;
                if (inSquadron >= 4) {
                    squadronIndex++;
                    inSquadron = 0;
                }
            }
        }

        fc.expanded = true;
        eventBus.publish(new FleetExpandedEvent(fc.fleetId));
    }

    private void collapseFleet(Entity fleetEntity, FleetComponent fc) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (fc.fleetId.equals(fmc.fleetId)) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            // Member ships carry a Bullet exterior body registered with the physics world by
            // ShipFactory; release it here so collapsing a fleet does not leak rigid bodies.
            shipFactory.disposeShip(e);
            engine.removeEntity(e);
        }

        fc.expanded = false;
        eventBus.publish(new FleetCollapsedEvent(fc.fleetId));
    }

    /**
     * Maps a fleet ship class to the procedural ship size tier used by {@link ShipFactory}.
     * {@link FleetShipClass} already carries its own {@link ShipSizeClass} (fighters/bombers/
     * corvettes = SMALL, frigates/destroyers/cruisers = MEDIUM, capitals = LARGE), so we use
     * that directly; SMALL is the fighter fallback if the class somehow has no size.
     */
    private ShipSizeClass mapToSizeClass(FleetShipClass shipClass) {
        if (shipClass == null || shipClass.sizeClass == null) return ShipSizeClass.SMALL;
        return shipClass.sizeClass;
    }
}
