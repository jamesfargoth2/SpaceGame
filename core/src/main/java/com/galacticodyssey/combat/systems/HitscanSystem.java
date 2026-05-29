package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Subscribes to {@link WeaponFiredEvent} and performs an entity-based raycast for hitscan weapons.
 *
 * <p>For each hitscan shot the system:
 * <ol>
 *   <li>Applies weapon spread to the aim direction.</li>
 *   <li>Publishes a {@link RecoilEvent} toward the shooter.</li>
 *   <li>Iterates all entities that have both {@link HitboxComponent} and {@link HealthComponent},
 *       finds the closest one whose centre lies within {@code hitRadius} of the ray and is within
 *       the weapon's maximum range.</li>
 *   <li>Determines the {@link HitRegion} from the normalised hit-height and publishes a
 *       {@link HitscanHitEvent}.</li>
 * </ol>
 *
 * <p>Priority: {@value #PRIORITY}.
 */
public class HitscanSystem extends EntitySystem {

    public static final int PRIORITY = 6;

    /** Cylinder radius used when testing whether an entity centre lies on the ray (metres). */
    private static final float HIT_RADIUS = 0.5f;

    private final EventBus eventBus;

    private Engine engine;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<RangedWeaponComponent> WEAPON_M =
        ComponentMapper.getFor(RangedWeaponComponent.class);
    private static final ComponentMapper<HitboxComponent> HITBOX_M =
        ComponentMapper.getFor(HitboxComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    /** Family for potential hitscan targets: need a position, a hitbox, and a health pool. */
    private static final Family TARGET_FAMILY =
        Family.all(TransformComponent.class, HitboxComponent.class, HealthComponent.class).get();

    // -------------------------------------------------------------------------
    // Reusable scratch vectors (only safe on the main/game thread)
    // -------------------------------------------------------------------------
    private final Vector3 rayDir = new Vector3();
    private final Vector3 toTarget = new Vector3();
    private final Vector3 closest = new Vector3();

    public HitscanSystem(EventBus eventBus) {
        super(PRIORITY);
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

    // -------------------------------------------------------------------------
    // HitscanSystem does its work purely via event subscription; update() is a no-op.
    // -------------------------------------------------------------------------
    @Override
    public void update(float deltaTime) { /* intentionally empty */ }

    // -------------------------------------------------------------------------
    // Event handler
    // -------------------------------------------------------------------------

    private void onWeaponFired(WeaponFiredEvent event) {
        // 1. Skip non-hitscan weapons
        if (!event.hitscan) return;

        Entity shooterEntity = event.shooter;
        TransformComponent shooterTransform = TRANSFORM_M.get(shooterEntity);
        RangedWeaponComponent weaponComp = WEAPON_M.get(shooterEntity);

        if (shooterTransform == null || weaponComp == null) return;

        Vector3 origin = event.muzzlePosition;

        // 2. Apply weapon spread: small random offset perpendicular to aim direction
        rayDir.set(event.aimDirection).nor();
        applySpread(rayDir, weaponComp.spread);

        // 3. (Recoil published by WeaponSystem.fireShot for all weapon types)

        // 4. Entity-based raycast: find closest valid target along the ray
        if (engine == null) return;

        Entity bestTarget = null;
        float bestDist = Float.MAX_VALUE;

        for (Entity candidate : engine.getEntitiesFor(TARGET_FAMILY)) {
            // Skip the shooter
            if (candidate == shooterEntity) continue;

            HealthComponent hp = HEALTH_M.get(candidate);
            if (hp == null || !hp.alive) continue;

            TransformComponent candidateTransform = TRANSFORM_M.get(candidate);
            if (candidateTransform == null) continue;

            // Vector from ray origin to target centre
            toTarget.set(candidateTransform.position).sub(origin);

            // Scalar projection of toTarget onto ray direction (gives distance along ray)
            float t = toTarget.dot(rayDir);
            if (t < 0f) continue; // target is behind the shooter

            // Range check (use t as approximate along-ray distance)
            if (t > weaponComp.range) continue;

            // Closest point on ray to target centre
            closest.set(rayDir).scl(t).add(origin);

            // Perpendicular distance from target centre to ray
            float perpDist = candidateTransform.position.dst(closest);
            if (perpDist > HIT_RADIUS) continue;

            if (t < bestDist) {
                bestDist = t;
                bestTarget = candidate;
            }
        }

        if (bestTarget == null) return;

        // 5. Determine hit region via hitHeightRatio
        TransformComponent targetTransform = TRANSFORM_M.get(bestTarget);
        HitboxComponent hitboxComp = HITBOX_M.get(bestTarget);

        // Calculate the hit point on the ray (closest point)
        Vector3 hitPoint = new Vector3(rayDir).scl(bestDist).add(origin);

        // Normalise hit height relative to target's feet (position.y) and bodyHeight
        float targetBottom = targetTransform.position.y - hitboxComp.bodyHeight * 0.5f;
        float hitHeightRatio = (hitPoint.y - targetBottom) / hitboxComp.bodyHeight;
        hitHeightRatio = MathUtils.clamp(hitHeightRatio, 0f, 1f);

        HitRegion hitRegion = hitboxComp.getRegionForHeight(hitHeightRatio);

        // Hit normal: direction from target centre to hit point (outward)
        Vector3 hitNormal = new Vector3(hitPoint).sub(targetTransform.position).nor();

        // 6. Publish HitscanHitEvent
        eventBus.publish(new HitscanHitEvent(
            shooterEntity,
            bestTarget,
            hitPoint,
            hitNormal,
            hitRegion,
            weaponComp.damage,
            weaponComp.damageType,
            weaponComp.ammoTypeId
        ));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a random cone offset to {@code dir} proportional to {@code spreadDegrees}.
     * The direction is re-normalised after the perturbation.
     */
    private void applySpread(Vector3 dir, float spreadDegrees) {
        if (spreadDegrees <= 0f) return;

        float spreadRad = spreadDegrees * MathUtils.degreesToRadians;
        float angle = MathUtils.random(0f, MathUtils.PI2);
        float magnitude = MathUtils.random(0f, spreadRad);

        // Build a perpendicular vector to perturb dir
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


}
