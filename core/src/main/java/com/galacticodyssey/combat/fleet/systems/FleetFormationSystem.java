package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Computes each fleet member's {@code localFormationOffset} each frame.
 *
 * <p>For every entity with {@link FleetMemberComponent} + {@link TransformComponent}, looks up
 * the owning fleet's {@link FleetFormationComponent}, fetches the slot offset from the
 * {@link FormationRegistry}, rotates it by the fleet's heading, and writes the world-local
 * anchor-relative position into {@link FleetMemberComponent#localFormationOffset}.
 *
 * <p>Priority {@value #PRIORITY} — runs after fleet-expansion / doctrine systems.
 */
public class FleetFormationSystem extends EntitySystem {

    public static final int PRIORITY = 7;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, TransformComponent.class
    ).get();

    private final FormationRegistry formationRegistry;
    private final EventBus eventBus;
    private final Vector3 tmpOffset = new Vector3();
    private final Quaternion tmpQuat = new Quaternion();
    private Engine engine;

    public FleetFormationSystem(FormationRegistry formationRegistry, EventBus eventBus) {
        super(PRIORITY);
        this.formationRegistry = formationRegistry;
        this.eventBus = eventBus;
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
        for (Entity member : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(member);
            if (fmc.fleetEntity == null) continue;

            FleetComponent fc = FLEET_M.get(fmc.fleetEntity);
            if (fc == null || !fc.expanded) continue;

            FleetFormationComponent ffc = FORMATION_M.get(fmc.fleetEntity);
            if (ffc == null) continue;

            FormationTemplate template = formationRegistry.get(ffc.formationTemplateId);
            if (template == null) continue;

            Vector3 slotOffset = template.getSlotOffset(fmc.formationSlotIndex);
            tmpOffset.set(slotOffset).scl(ffc.spacingScale);
            tmpQuat.setEulerAngles(ffc.headingYaw, ffc.headingPitch, 0f);
            tmpOffset.mul(tmpQuat);

            fmc.localFormationOffset.set(
                ffc.localAnchorX + tmpOffset.x,
                ffc.localAnchorY + tmpOffset.y,
                ffc.localAnchorZ + tmpOffset.z
            );
        }
    }
}
