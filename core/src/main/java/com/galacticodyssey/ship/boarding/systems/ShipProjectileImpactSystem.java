package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sphere-proximity collision between active projectiles and ships. On contact with a ship
 * the projectile does not own, publishes a {@link ShipDamageEvent} and removes the projectile.
 * This is the in-game producer of ship damage (no ship-vs-ship hull damage existed before).
 */
public class ShipProjectileImpactSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    /** Coarse hull radius for impact in metres. Replace with per-hull bounds later. */
    private static final float IMPACT_RADIUS = 8f;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJ_M =
        ComponentMapper.getFor(ProjectileComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> projectiles;
    private ImmutableArray<Entity> ships;
    private final List<Entity> toRemove = new ArrayList<>();

    public ShipProjectileImpactSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        projectiles = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, TransformComponent.class).get());
        ships = engine.getEntitiesFor(
            Family.all(ShipDataComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        projectiles = null;
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (projectiles == null || ships == null) return;
        toRemove.clear();

        for (int i = 0, n = projectiles.size(); i < n; i++) {
            Entity proj = projectiles.get(i);
            ProjectileComponent pc = PROJ_M.get(proj);
            Vector3 ppos = TRANSFORM_M.get(proj).position;

            for (int j = 0, m = ships.size(); j < m; j++) {
                Entity ship = ships.get(j);
                if (pc.owner == ship) continue;
                Vector3 spos = TRANSFORM_M.get(ship).position;
                if (ppos.dst2(spos) <= IMPACT_RADIUS * IMPACT_RADIUS) {
                    eventBus.publish(new ShipDamageEvent(
                        ship, pc.owner, pc.damage, pc.damageType, ppos));
                    toRemove.add(proj);
                    break;
                }
            }
        }

        for (int i = 0; i < toRemove.size(); i++) {
            getEngine().removeEntity(toRemove.get(i));
        }
    }
}
