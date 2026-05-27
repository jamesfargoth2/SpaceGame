package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.combat.ProNavGuidance;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent.GuidancePhase;

public class GuidedProjectileSystem extends IteratingSystem {

    public static final int PRIORITY = 8;

    private final EventBus eventBus;
    private Engine engine;
    private final Array<Entity> toRemove = new Array<>();

    private static final ComponentMapper<GuidedProjectileComponent> GUIDED_M =
        ComponentMapper.getFor(GuidedProjectileComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJ_M =
        ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    public GuidedProjectileSystem(EventBus eventBus) {
        super(Family.all(
            GuidedProjectileComponent.class,
            ProjectileComponent.class,
            TransformComponent.class
        ).get(), PRIORITY);
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
        super.update(deltaTime);
        if (engine != null) {
            for (Entity e : toRemove) {
                engine.removeEntity(e);
            }
        }
        toRemove.clear();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        GuidedProjectileComponent guided = GUIDED_M.get(entity);
        ProjectileComponent proj = PROJ_M.get(entity);
        TransformComponent transform = TRANSFORM_M.get(entity);

        float stepDist = proj.velocity.len() * deltaTime;
        guided.distanceTraveled += stepDist;

        // Integrate position (guided projectiles move themselves so they work
        // independently of ProjectileSystem's family, which excludes guided entities
        // when both systems are present)
        transform.position.mulAdd(proj.velocity, deltaTime);
        proj.distanceTravelled += stepDist;

        updatePhase(guided, proj, transform, deltaTime);

        if (guided.phase == GuidancePhase.DETONATION) {
            detonate(entity, proj, transform);
            return;
        }

        if (guided.targetEntity == null) return;

        TransformComponent targetTransform = TRANSFORM_M.get(guided.targetEntity);
        if (targetTransform == null) return;

        if (guided.distanceTraveled < guided.armingDistance) return;

        float distToTarget = transform.position.dst(targetTransform.position);
        if (proj.proximityFuseRadius > 0f && distToTarget <= proj.proximityFuseRadius) {
            guided.phase = GuidancePhase.DETONATION;
            detonate(entity, proj, transform);
            return;
        }

        applyGuidance(guided, proj, transform, targetTransform, deltaTime);
    }

    private void updatePhase(GuidedProjectileComponent guided,
                             ProjectileComponent proj,
                             TransformComponent transform,
                             float deltaTime) {
        if (guided.phase == GuidancePhase.DETONATION) return;

        if (guided.phase == GuidancePhase.BOOST) {
            guided.boostTimer += deltaTime;
            if (guided.boostTimer >= guided.boostDuration || guided.fuelRemaining <= 0f) {
                guided.phase = GuidancePhase.COAST;
            }
        }

        if (guided.targetEntity != null && guided.phase != GuidancePhase.BOOST) {
            TransformComponent targetTransform = TRANSFORM_M.get(guided.targetEntity);
            if (targetTransform != null) {
                float dist = transform.position.dst(targetTransform.position);
                if (dist <= guided.terminalRange) {
                    guided.phase = GuidancePhase.TERMINAL;
                }
            }
        }
    }

    private void applyGuidance(GuidedProjectileComponent guided,
                               ProjectileComponent proj,
                               TransformComponent missileTransform,
                               TransformComponent targetTransform,
                               float deltaTime) {
        Vector3 targetVel = Pools.obtain(Vector3.class).setZero();

        ProjectileComponent targetProj = PROJ_M.get(guided.targetEntity);
        if (targetProj != null) {
            targetVel.set(targetProj.velocity);
        }

        Vector3 accel;
        if (guided.phase == GuidancePhase.TERMINAL) {
            // Pure pursuit in terminal phase
            accel = Pools.obtain(Vector3.class)
                .set(targetTransform.position)
                .sub(missileTransform.position)
                .nor()
                .scl(guided.maxAcceleration);
        } else {
            accel = ProNavGuidance.steer(
                missileTransform.position, proj.velocity,
                targetTransform.position, targetVel,
                guided.navigationGain
            );
        }

        // Clamp acceleration
        if (accel.len2() > guided.maxAcceleration * guided.maxAcceleration) {
            accel.nor().scl(guided.maxAcceleration);
        }

        proj.velocity.mulAdd(accel, deltaTime);

        Pools.free(targetVel);
        Pools.free(accel);

        if (guided.phase == GuidancePhase.BOOST && guided.fuelRemaining > 0f) {
            guided.fuelRemaining -= deltaTime / guided.boostDuration;
            guided.fuelRemaining = Math.max(0f, guided.fuelRemaining);
        }
    }

    private void detonate(Entity entity, ProjectileComponent proj,
                          TransformComponent transform) {
        eventBus.publish(new DetonationEvent(
            proj.owner,
            transform.position,
            proj.damage,
            proj.damageType,
            proj.areaOfEffect
        ));
        toRemove.add(entity);
    }
}
