package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.GrenadeBounceEvent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class ProjectileSystem extends IteratingSystem {

    public static final int PRIORITY = 7;

    private static final float COLLISION_RADIUS = 1.0f;
    private static final Vector3 GRAVITY = new Vector3(0f, -9.81f, 0f);

    private final EventBus eventBus;
    private Engine engine;
    private final Array<Entity> toRemove = new Array<>();
    private GrenadeDataRegistry grenadeRegistry;

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

    public void setGrenadeDataRegistry(GrenadeDataRegistry registry) {
        this.grenadeRegistry = registry;
    }

    public ProjectileSystem(EventBus eventBus) {
        super(Family.all(ProjectileComponent.class, TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
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

    private void onWeaponFired(WeaponFiredEvent event) {
        if (event.hitscan) return;
        if (engine == null) return;

        Entity shooterEntity = event.shooter;
        TransformComponent shooterTransform = TRANSFORM_M.get(shooterEntity);
        RangedWeaponComponent weaponComp = WEAPON_M.get(shooterEntity);

        if (shooterTransform == null || weaponComp == null) return;

        float speed = weaponComp.projectileSpeed != null ? weaponComp.projectileSpeed : 40f;
        float lifetime = (speed > 0f) ? weaponComp.range / speed : 2f;

        Vector3 dir = Pools.obtain(Vector3.class).set(event.aimDirection).nor();

        Entity projectile = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(event.muzzlePosition);

        ProjectileComponent proj = engine.createComponent(ProjectileComponent.class);
        proj.velocity.set(dir).scl(speed);
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

        // Attach GrenadeComponent if this is a grenade launcher
        if (weaponComp.grenadeTypeId != null && grenadeRegistry != null) {
            GrenadeData gData = grenadeRegistry.get(weaponComp.grenadeTypeId);
            if (gData != null) {
                GrenadeComponent gc = new GrenadeComponent();
                gc.grenadeTypeId = gData.id;
                gc.fuseType = gData.fuseType;
                gc.fuseDuration = gData.fuseDuration;
                gc.fuseTimer = gData.fuseDuration;
                gc.cookTime = 0f;
                gc.cookable = false;
                gc.bounceRestitution = gData.bounceRestitution;
                gc.maxBounces = gData.maxBounces;
                gc.damage = gData.damage;
                gc.blastRadius = gData.blastRadius;
                gc.blastFraction = gData.blastFraction;
                gc.thermalFraction = gData.thermalFraction;
                gc.fragmentFraction = gData.fragmentFraction;
                gc.isDirectional = gData.isDirectional;
                projectile.add(gc);
            }
        }

        engine.addEntity(projectile);

        Pools.free(dir);
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
        ProjectileComponent proj = PROJECTILE_M.get(entity);
        TransformComponent transform = TRANSFORM_M.get(entity);

        proj.age += deltaTime;
        if (proj.age >= proj.lifetime) {
            toRemove.add(entity);
            return;
        }

        // --- Physics integration ---

        Vector3 accel = Pools.obtain(Vector3.class).setZero();

        if (proj.affectedByGravity) {
            accel.add(GRAVITY);
        }

        if (proj.dragCoeff > 0f && proj.crossSection > 0f) {
            float vSq = proj.velocity.len2();
            if (vSq > 0.001f) {
                // Simplified drag: F = 0.5 * rho * v^2 * Cd * A  (rho=1.225 at sea level)
                float airDensity = 1.225f;
                float dragMag = 0.5f * airDensity * vSq * proj.dragCoeff * proj.crossSection;
                Vector3 drag = Pools.obtain(Vector3.class)
                    .set(proj.velocity).nor().scl(-dragMag / proj.mass);
                accel.add(drag);
                Pools.free(drag);
            }
        }

        proj.velocity.mulAdd(accel, deltaTime);
        Pools.free(accel);

        float stepDist = proj.velocity.len() * deltaTime;
        transform.position.mulAdd(proj.velocity, deltaTime);
        proj.distanceTravelled += stepDist;

        if (proj.distanceTravelled >= proj.maxRange) {
            toRemove.add(entity);
            return;
        }

        // --- Ground-plane bounce for grenades ---
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(entity);
        if (gc != null && gc.fuseType != FuseType.IMPACT && transform.position.y <= 0f) {
            transform.position.y = 0f;
            if (gc.bounceCount < gc.maxBounces) {
                proj.velocity.y = -proj.velocity.y * gc.bounceRestitution;
                proj.velocity.x *= gc.bounceRestitution;
                proj.velocity.z *= gc.bounceRestitution;
                gc.bounceCount++;
                if (proj.velocity.len() < 0.5f) {
                    proj.velocity.setZero();
                }
                eventBus.publish(new GrenadeBounceEvent(entity, transform.position, Vector3.Y));
            } else {
                proj.velocity.setZero();
            }
        }

        // --- Collision detection ---
        if (engine == null) return;

        ImmutableArray<Entity> targets = engine.getEntitiesFor(TARGET_FAMILY);
        for (int i = 0, n = targets.size(); i < n; i++) {
            Entity candidate = targets.get(i);
            if (candidate == proj.owner) continue;

            HealthComponent hp = HEALTH_M.get(candidate);
            if (hp == null || !hp.alive) continue;

            TransformComponent targetTransform = TRANSFORM_M.get(candidate);
            if (targetTransform == null) continue;

            float dist = transform.position.dst(targetTransform.position);
            if (dist <= COLLISION_RADIUS) {
                // Check if this projectile is a grenade
                GrenadeComponent grenadeComp = GrenadeComponent.MAPPER.get(entity);
                if (grenadeComp != null) {
                    if (grenadeComp.fuseType == FuseType.IMPACT) {
                        grenadeComp.fuseTimer = 0f;
                        proj.velocity.setZero();
                    } else {
                        if (grenadeComp.bounceCount < grenadeComp.maxBounces) {
                            Vector3 normal = new Vector3(transform.position).sub(targetTransform.position).nor();
                            float dot = proj.velocity.dot(normal);
                            proj.velocity.mulAdd(normal, -2f * dot);
                            proj.velocity.scl(grenadeComp.bounceRestitution);
                            grenadeComp.bounceCount++;
                            if (proj.velocity.len() < 0.5f) {
                                proj.velocity.setZero();
                            }
                            eventBus.publish(new GrenadeBounceEvent(entity, transform.position, normal));
                        } else {
                            proj.velocity.setZero();
                        }
                    }
                    return; // Skip normal projectile hit processing for ALL grenades
                }

                // Normal projectile hit (non-grenade)
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
                return;
            }
        }
    }
}
