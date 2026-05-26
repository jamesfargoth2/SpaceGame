package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;

public class TurretTrackingSystem extends EntitySystem {
    private static final int PRIORITY = 3;
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpWorldPos = new Vector3();
    private ImmutableArray<Entity> entities;

    public TurretTrackingSystem() { super(PRIORITY); }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ShipHardpointComponent hpc = entity.getComponent(ShipHardpointComponent.class);
            TransformComponent shipTc = entity.getComponent(TransformComponent.class);

            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.isEmpty()) continue;
                if (hp.currentState == HardpointState.DISABLED) continue;

                if (hpc.currentTarget == null) {
                    hp.currentState = HardpointState.IDLE;
                    continue;
                }

                TransformComponent targetTc = hpc.currentTarget.getComponent(TransformComponent.class);
                if (targetTc == null) {
                    hp.currentState = HardpointState.IDLE;
                    continue;
                }

                tmpWorldPos.set(hp.position).add(shipTc.position);
                tmpDir.set(targetTc.position).sub(tmpWorldPos).nor();
                float angle = angleToBearing(tmpDir, shipTc);

                if (hp.type == HardpointType.TURRET) {
                    hp.currentState = HardpointState.TRACKING;
                } else if (hp.isInArc(angle)) {
                    hp.currentState = HardpointState.TRACKING;
                } else {
                    hp.currentState = HardpointState.IDLE;
                }
            }
        }
    }

    private float angleToBearing(Vector3 dirToTarget, TransformComponent shipTc) {
        Vector3 forward = new Vector3(0, 0, 1);
        shipTc.rotation.transform(forward);
        float dot = forward.dot(dirToTarget);
        return MathUtils.acos(MathUtils.clamp(dot, -1f, 1f)) * MathUtils.radiansToDegrees;
    }
}
