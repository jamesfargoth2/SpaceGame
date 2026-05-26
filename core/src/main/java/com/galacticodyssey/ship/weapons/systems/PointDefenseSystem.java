package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.events.PointDefenseEngagedEvent;

public class PointDefenseSystem extends EntitySystem {
    private static final int PRIORITY = 5;
    private final EventBus eventBus;
    private final Vector3 tmpDist = new Vector3();
    private ImmutableArray<Entity> ships;
    private ImmutableArray<Entity> projectiles;

    public PointDefenseSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        ships = engine.getEntitiesFor(
            Family.all(ShipHardpointComponent.class, TransformComponent.class).get());
        projectiles = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < ships.size(); i++) {
            Entity ship = ships.get(i);
            ShipHardpointComponent hpc = ship.getComponent(ShipHardpointComponent.class);
            TransformComponent shipTc = ship.getComponent(TransformComponent.class);

            for (Hardpoint hp : hpc.hardpoints) {
                if (hp.type != HardpointType.POINT_DEFENSE) continue;
                if (hp.isEmpty()) continue;
                if (hp.fireTimer > 0) {
                    hp.fireTimer -= deltaTime;
                    continue;
                }

                Entity closest = findClosestThreat(ship, shipTc, hp.mountedWeapon.range);
                if (closest != null) {
                    hp.fireTimer = 1f / hp.mountedWeapon.fireRate;
                    eventBus.publish(new PointDefenseEngagedEvent(ship, closest));
                }
            }
        }
    }

    private Entity findClosestThreat(Entity ship, TransformComponent shipTc, float range) {
        Entity closest = null;
        float closestDist = range * range;

        for (int i = 0; i < projectiles.size(); i++) {
            Entity proj = projectiles.get(i);
            ProjectileComponent pc = proj.getComponent(ProjectileComponent.class);
            if (pc.owner == ship) continue;

            TransformComponent projTc = proj.getComponent(TransformComponent.class);
            float dist2 = tmpDist.set(projTc.position).sub(shipTc.position).len2();
            if (dist2 < closestDist) {
                closestDist = dist2;
                closest = proj;
            }
        }
        return closest;
    }
}
