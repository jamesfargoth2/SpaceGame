package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
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
    private Engine engine;

    public FleetExpansionSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier,
                                FormationRegistry formationRegistry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
        this.formationRegistry = formationRegistry;
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
                Entity ship = new Entity();

                FleetMemberComponent fmc = new FleetMemberComponent();
                fmc.fleetEntity = fleetEntity;
                fmc.fleetId = fc.fleetId;
                fmc.squadronIndex = squadronIndex;
                fmc.role = entry.shipClass.defaultRole;
                fmc.formationSlotIndex = slotIndex;
                ship.add(fmc);

                TransformComponent tc = new TransformComponent();
                tc.position.set(ffc.localAnchorX, ffc.localAnchorY, ffc.localAnchorZ);
                ship.add(tc);

                engine.addEntity(ship);
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
            engine.removeEntity(e);
        }

        fc.expanded = false;
        eventBus.publish(new FleetCollapsedEvent(fc.fleetId));
    }
}
