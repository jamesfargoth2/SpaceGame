package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.data.AmmoTypeData;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.GrenadeBounceEvent;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;

public class ProjectileSystem extends IteratingSystem implements Disposable {

    public static final int PRIORITY = 7;

    /** Sphere radius for grenade–entity proximity checks. */
    private static final float GRENADE_COLLISION_RADIUS = 1.0f;
    /** Fallback capsule radius for bullet swept-CCD when no HitboxComponent is present. */
    private static final float BULLET_COLLISION_RADIUS = 0.5f;

    private static final Vector3 GRAVITY = new Vector3(0f, -9.81f, 0f);

    private final EventBus eventBus;
    private Engine engine;
    private final Array<Entity> toRemove = new Array<>();
    private GrenadeDataRegistry grenadeRegistry;
    private WeaponDataRegistry weaponDataRegistry;

    private BulletPhysicsSystem bulletPhysicsSystem;
    private ClosestRayResultCallback rayCallback;
    private final Vector3 tempHitNormal = new Vector3();

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
    private static final ComponentMapper<HitboxComponent> HITBOX_M =
        ComponentMapper.getFor(HitboxComponent.class);

    public void setGrenadeDataRegistry(GrenadeDataRegistry registry) {
        this.grenadeRegistry = registry;
    }

    public void setWeaponDataRegistry(WeaponDataRegistry registry) {
        this.weaponDataRegistry = registry;
    }

    public void setBulletPhysicsSystem(BulletPhysicsSystem bps) {
        this.bulletPhysicsSystem = bps;
        if (rayCallback != null) {
            rayCallback.dispose();
            rayCallback = null;
        }
        if (bps != null) {
            rayCallback = new ClosestRayResultCallback(new Vector3(), new Vector3());
        }
    }

    @Override
    public void dispose() {
        if (rayCallback != null) {
            rayCallback.dispose();
            rayCallback = null;
        }
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

        // Resolve projectile speed: component value → ammo registry → fallback
        float speed;
        AmmoTypeData ammo = resolveAmmo(weaponComp.ammoTypeId);
        if (weaponComp.projectileSpeed != null) {
            speed = weaponComp.projectileSpeed;
        } else if (ammo != null && ammo.projectileSpeed != null) {
            speed = ammo.projectileSpeed;
        } else {
            speed = 40f;
        }

        float lifetime = (speed > 0f && weaponComp.range > 0f) ? weaponComp.range / speed : 2f;

        Vector3 dir = Pools.obtain(Vector3.class).set(event.aimDirection).nor();
        float totalSpread = weaponComp.spread + weaponComp.currentHeatSpread;
        if (totalSpread > 0f) applySpread(dir, totalSpread);

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
        proj.maxRange = weaponComp.range > 0f ? weaponComp.range : Float.MAX_VALUE;

        // Physics from ammo data
        if (ammo != null) {
            proj.mass = ammo.mass > 0f ? ammo.mass : 1f;
            proj.dragCoeff = ammo.dragCoeff;
            proj.crossSection = ammo.crossSection;
            proj.affectedByGravity = ammo.affectedByGravity;
        }

        projectile.add(transform);
        projectile.add(proj);

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

    private AmmoTypeData resolveAmmo(String ammoTypeId) {
        if (ammoTypeId == null || weaponDataRegistry == null) return null;
        return weaponDataRegistry.getAmmoType(ammoTypeId);
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

        // Save position before integration so swept CCD can test the full tick's travel
        proj.prevPosition.set(transform.position);

        // --- Physics integration ---
        Vector3 accel = Pools.obtain(Vector3.class).setZero();

        if (proj.affectedByGravity) {
            accel.add(GRAVITY);
        }

        if (proj.dragCoeff > 0f && proj.crossSection > 0f && proj.mass > 0f) {
            float vSq = proj.velocity.len2();
            if (vSq > 0.001f) {
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

        // --- Bullet physics world collision (terrain, boxes, entity physics bodies) ---
        if (bulletPhysicsSystem != null && rayCallback != null && gc == null) {
            btDiscreteDynamicsWorld world = bulletPhysicsSystem.getDynamicsWorld();
            if (world != null) {
                rayCallback.setCollisionObject(null);
                rayCallback.setClosestHitFraction(1f);
                rayCallback.setRayFromWorld(proj.prevPosition);
                rayCallback.setRayToWorld(transform.position);
                world.rayTest(proj.prevPosition, transform.position, rayCallback);

                if (rayCallback.hasHit()) {
                    btCollisionObject hitObj = rayCallback.getCollisionObject();
                    Entity hitEntity = (hitObj instanceof btRigidBody)
                        ? bulletPhysicsSystem.getEntityForBody((btRigidBody) hitObj)
                        : null;

                    if (hitEntity != proj.owner) {
                        float fraction = rayCallback.getClosestHitFraction();
                        Vector3 hitPoint = new Vector3(proj.prevPosition)
                            .lerp(transform.position, fraction);
                        rayCallback.getHitNormalWorld(tempHitNormal);
                        tempHitNormal.nor();

                        HitRegion region = HitRegion.TORSO;
                        if (hitEntity != null) {
                            TransformComponent targetTransform = TRANSFORM_M.get(hitEntity);
                            HitboxComponent hitbox = HITBOX_M.get(hitEntity);
                            if (targetTransform != null) {
                                float bodyHeight = hitbox != null ? hitbox.bodyHeight : 1.8f;
                                float ratio = bodyHeight > 0f
                                    ? MathUtils.clamp(
                                        (hitPoint.y - targetTransform.position.y) / bodyHeight,
                                        0f, 1f)
                                    : 0.5f;
                                if (hitbox != null) region = hitbox.getRegionForHeight(ratio);
                                else if (ratio >= 0.85f) region = HitRegion.HEAD;
                                else if (ratio >= 0.50f) region = HitRegion.TORSO;
                                else if (ratio >= 0.25f) region = HitRegion.ARMS;
                                else region = HitRegion.LEGS;
                            }
                        }

                        eventBus.publish(new ProjectileHitEvent(
                            proj.owner, hitEntity, hitPoint, new Vector3(tempHitNormal),
                            proj.damage, proj.damageType, proj.areaOfEffect,
                            proj.ammoTypeId, region
                        ));
                        toRemove.add(entity);
                        return;
                    }
                }
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

            if (gc != null) {
                // --- Grenade: simple end-of-frame sphere check ---
                float dist = transform.position.dst(targetTransform.position);
                if (dist <= GRENADE_COLLISION_RADIUS) {
                    if (gc.fuseType == FuseType.IMPACT) {
                        gc.fuseTimer = 0f;
                        proj.velocity.setZero();
                    } else {
                        if (gc.bounceCount < gc.maxBounces) {
                            Vector3 normal = new Vector3(transform.position).sub(targetTransform.position).nor();
                            float dot = proj.velocity.dot(normal);
                            proj.velocity.mulAdd(normal, -2f * dot);
                            proj.velocity.scl(gc.bounceRestitution);
                            gc.bounceCount++;
                            if (proj.velocity.len() < 0.5f) {
                                proj.velocity.setZero();
                            }
                            eventBus.publish(new GrenadeBounceEvent(entity, transform.position, normal));
                        } else {
                            proj.velocity.setZero();
                        }
                    }
                    return;
                }
            } else {
                // --- Bullet: swept CCD against the target's vertical capsule ---
                HitboxComponent hitbox = HITBOX_M.get(candidate);
                HitRegion region = computeSweptHit(
                    proj.prevPosition, transform.position, targetTransform.position, hitbox);
                if (region != null) {
                    eventBus.publish(new ProjectileHitEvent(
                        proj.owner,
                        candidate,
                        new Vector3(transform.position),
                        proj.damage,
                        proj.damageType,
                        proj.areaOfEffect,
                        proj.ammoTypeId,
                        region
                    ));
                    toRemove.add(entity);
                    return;
                }
            }
        }
    }

    private static void applySpread(Vector3 dir, float spreadDegrees) {
        float spreadRad = spreadDegrees * MathUtils.degreesToRadians;
        float angle = MathUtils.random(0f, MathUtils.PI2);
        float magnitude = MathUtils.random(0f, spreadRad);
        Vector3 perp = new Vector3();
        if (Math.abs(dir.x) < 0.9f) {
            perp.set(1f, 0f, 0f).crs(dir).nor();
        } else {
            perp.set(0f, 1f, 0f).crs(dir).nor();
        }
        Vector3 perp2 = new Vector3(dir).crs(perp).nor();
        dir.mulAdd(perp,  MathUtils.cos(angle) * magnitude);
        dir.mulAdd(perp2, MathUtils.sin(angle) * magnitude);
        dir.nor();
    }

    /**
     * Swept CCD: tests whether the bullet path segment [p0→p1] passes through the vertical capsule
     * centred on the target's feet with the given body height and collision radius.
     *
     * @return the HitRegion if the path intersects the capsule, or null on a miss.
     */
    private static HitRegion computeSweptHit(Vector3 p0, Vector3 p1,
                                              Vector3 targetFeet, HitboxComponent hitbox) {
        float colRadius  = hitbox != null ? hitbox.collisionRadius : BULLET_COLLISION_RADIUS;
        float bodyHeight = hitbox != null ? hitbox.bodyHeight : 1.8f;

        // Closest t on the bullet segment to the capsule's vertical axis in the XZ plane
        float sdx = p1.x - p0.x;
        float sdz = p1.z - p0.z;
        float segLen2 = sdx * sdx + sdz * sdz;

        float t;
        if (segLen2 < 1e-8f) {
            t = 0f;
        } else {
            float wx = targetFeet.x - p0.x;
            float wz = targetFeet.z - p0.z;
            t = MathUtils.clamp((wx * sdx + wz * sdz) / segLen2, 0f, 1f);
        }

        // Bullet world position at closest-approach t
        float bx = p0.x + t * (p1.x - p0.x);
        float by = p0.y + t * (p1.y - p0.y);
        float bz = p0.z + t * (p1.z - p0.z);

        // Horizontal (XZ) distance to capsule axis — must be within radius
        float hDistSq = (bx - targetFeet.x) * (bx - targetFeet.x)
                      + (bz - targetFeet.z) * (bz - targetFeet.z);
        if (hDistSq > colRadius * colRadius) return null;

        // Vertical extent: body column plus hemispherical end caps of radius colRadius
        float footY = targetFeet.y;
        float headY = footY + bodyHeight;
        if (by < footY - colRadius || by > headY + colRadius) return null;

        // Map vertical hit position to a body region
        float clampedY = Math.max(footY, Math.min(headY, by));
        float ratio = bodyHeight > 0f ? (clampedY - footY) / bodyHeight : 0.5f;
        if (hitbox != null) return hitbox.getRegionForHeight(ratio);

        if (ratio >= 0.85f) return HitRegion.HEAD;
        if (ratio >= 0.50f) return HitRegion.TORSO;
        if (ratio >= 0.25f) return HitRegion.ARMS;
        return HitRegion.LEGS;
    }
}
