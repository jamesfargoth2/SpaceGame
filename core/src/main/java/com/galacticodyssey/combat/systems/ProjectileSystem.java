package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Moves projectile entities each frame, detects collisions with hittable targets, and publishes
 * {@link ProjectileHitEvent} on contact.
 *
 * <p>Projectile travel direction is encoded into the {@link TransformComponent#rotation} quaternion
 * as a raw float3 (x, y, z components hold the normalised direction; w is set to 0).  This avoids
 * adding a new field to an existing component while keeping direction co-located with the entity's
 * transform.
 *
 * <p>Priority: {@value #PRIORITY} (runs after {@link HitscanSystem}).
 */
public class ProjectileSystem extends IteratingSystem {

    public static final int PRIORITY = 7;

    /** Sphere radius for simple distance-based collision detection (metres). */
    private static final float COLLISION_RADIUS = 1.0f;

    private final EventBus eventBus;

    /** Engine reference stored via {@link #addedToEngine} / {@link #removedFromEngine}. */
    private Engine engine;

    /** Deferred removal list — populated during iteration, flushed in {@link #update}. */
    private final Array<Entity> toRemove = new Array<>();

    /** Entities that can be hit: need a position, hitbox, and health pool. */
    private static final Family TARGET_FAMILY =
        Family.all(TransformComponent.class, HitboxComponent.class, HealthComponent.class).get();

    private static final ComponentMapper<ProjectileComponent> PROJECTILE_M =
        ComponentMapper.getFor(ProjectileComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> WEAPON_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    public ProjectileSystem(EventBus eventBus) {
        super(Family.all(ProjectileComponent.class, TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
    }

    // -------------------------------------------------------------------------
    // Engine lifecycle
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Subscription handler — spawns a projectile entity for non-hitscan shots
    // -------------------------------------------------------------------------

    private void onWeaponFired(WeaponFiredEvent event) {
        if (event.hitscan) return;
        if (engine == null) return;

        Entity shooterEntity = event.shooter;
        TransformComponent shooterTransform = TRANSFORM_M.get(shooterEntity);
        RangedWeaponComponent weaponComp = WEAPON_M.get(shooterEntity);

        if (shooterTransform == null || weaponComp == null) return;

        float speed = weaponComp.projectileSpeed != null ? weaponComp.projectileSpeed : 40f;
        float lifetime = (speed > 0f) ? weaponComp.range / speed : 2f;

        // Normalise aim direction
        Vector3 dir = new Vector3(event.aimDirection).nor();

        // Build projectile entity
        Entity projectile = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(shooterTransform.position);
        // Store direction in the rotation quaternion's x/y/z fields as a raw float3
        transform.rotation.x = dir.x;
        transform.rotation.y = dir.y;
        transform.rotation.z = dir.z;
        transform.rotation.w = 0f;

        ProjectileComponent proj = engine.createComponent(ProjectileComponent.class);
        proj.speed = speed;
        proj.damage = weaponComp.damage;
        proj.damageType = weaponComp.damageType;
        proj.owner = shooterEntity;
        proj.lifetime = lifetime;
        proj.age = 0f;
        proj.areaOfEffect = 0f;
        proj.ammoTypeId = weaponComp.ammoTypeId;

        projectile.add(transform);
        projectile.add(proj);
        engine.addEntity(projectile);
    }

    // -------------------------------------------------------------------------
    // IteratingSystem — update flushes deferred removals after iteration
    // -------------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);

        // Flush deferred removals after the iteration is complete
        if (engine != null) {
            for (Entity e : toRemove) {
                engine.removeEntity(e);
            }
        }
        toRemove.clear();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ProjectileComponent proj = PROJECTILE_M.get(entity);
        TransformComponent transform = TRANSFORM_M.get(entity);

        // 1. Age and expire
        proj.age += deltaTime;
        if (proj.age >= proj.lifetime) {
            toRemove.add(entity);
            return;
        }

        // 2. Move along stored direction (rotation.x/y/z)
        float dx = transform.rotation.x;
        float dy = transform.rotation.y;
        float dz = transform.rotation.z;

        transform.position.x += dx * proj.speed * deltaTime;
        transform.position.y += dy * proj.speed * deltaTime;
        transform.position.z += dz * proj.speed * deltaTime;

        // 3. Collision detection — iterate all valid target entities
        if (engine == null) return;

        ImmutableArray<Entity> targets = engine.getEntitiesFor(TARGET_FAMILY);
        for (int i = 0, n = targets.size(); i < n; i++) {
            Entity candidate = targets.get(i);

            // Skip the owner
            if (candidate == proj.owner) continue;

            HealthComponent hp = HEALTH_M.get(candidate);
            if (hp == null || !hp.alive) continue;

            TransformComponent targetTransform = TRANSFORM_M.get(candidate);
            if (targetTransform == null) continue;

            float dist = transform.position.dst(targetTransform.position);
            if (dist <= COLLISION_RADIUS) {
                eventBus.publish(new ProjectileHitEvent(
                    proj.owner,
                    candidate,
                    new Vector3(transform.position),
                    proj.damage,
                    proj.damageType,
                    proj.areaOfEffect,
                    proj.ammoTypeId
                ));
                toRemove.add(entity);
                return; // projectile consumed by first hit
            }
        }
    }
}
