package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.ExplosionData;
import com.galacticodyssey.combat.components.ExplosionAffectedComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.BlastDamageEvent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.EMPHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class ExplosionSystem extends EntitySystem {

    public static final int PRIORITY = 9;

    private static final float BASE_PRESSURE = 101325f;
    private static final float ENERGY_PER_DAMAGE = 1000f;

    private final EventBus eventBus;
    private final Array<DetonationEvent> pendingDetonations = new Array<>();
    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private static final Family AFFECTED_FAMILY = Family.all(
        TransformComponent.class, HealthComponent.class, ExplosionAffectedComponent.class
    ).get();

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ExplosionAffectedComponent> AFFECTED_M =
        ComponentMapper.getFor(ExplosionAffectedComponent.class);

    public ExplosionSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(DetonationEvent.class, this::onDetonation);
    }

    private void onDetonation(DetonationEvent event) {
        pendingDetonations.add(event);
    }

    @Override
    public void update(float deltaTime) {
        if (pendingDetonations.size == 0) return;

        Engine engine = getEngine();
        if (engine == null) return;

        ImmutableArray<Entity> targets = engine.getEntitiesFor(AFFECTED_FAMILY);

        for (int i = 0; i < pendingDetonations.size; i++) {
            processDetonation(pendingDetonations.get(i), targets);
        }
        pendingDetonations.clear();
    }

    private void processDetonation(DetonationEvent event, ImmutableArray<Entity> targets) {
        ExplosionData data = buildExplosionData(event);

        Vector3 toTarget = vectorPool.obtain();
        Vector3 impulseVec = vectorPool.obtain();
        try {
            for (int i = 0, n = targets.size(); i < n; i++) {
                Entity target = targets.get(i);
                HealthComponent health = HEALTH_M.get(target);
                if (health == null || !health.alive) continue;

                TransformComponent transform = TRANSFORM_M.get(target);
                if (transform == null) continue;

                ExplosionAffectedComponent affected = AFFECTED_M.get(target);

                toTarget.set(transform.position).sub(data.origin);
                float distance = toTarget.len();

                if (distance > event.areaOfEffect || distance <= 0f) continue;

                Vector3 dirNormalized = toTarget.nor();

                // Blast damage
                float overpressure = computeOverpressure(data, distance);
                float blastDamage = overpressure * affected.projectedArea * data.blastFraction / affected.armorFactor;

                // Directional (shaped charge) multiplier
                if (data.isDirectional) {
                    blastDamage *= directionalMultiplier(data, dirNormalized);
                }

                // Thermal damage
                float thermalEnergy = data.totalEnergy * data.thermalFraction;
                float flux = thermalEnergy / (4f * MathUtils.PI * distance * distance);
                float thermalDamage = flux * affected.thermalAbsorptivity;

                float totalDamage = blastDamage + thermalDamage;

                if (totalDamage > 0f) {
                    impulseVec.set(dirNormalized).scl(overpressure * affected.projectedArea);
                    eventBus.publish(new BlastDamageEvent(target, totalDamage, data.origin, impulseVec));
                }

                // EMP
                if (data.empRadius > 0f && distance <= data.empRadius) {
                    float strength = (data.empRadius * data.empRadius) / (distance * distance);
                    float effectStrength = strength * (1f - affected.empHardeningFactor);
                    if (effectStrength > 0f) {
                        eventBus.publish(new EMPHitEvent(target, effectStrength));
                    }
                }
            }
        } finally {
            vectorPool.free(toTarget);
            vectorPool.free(impulseVec);
        }
    }

    private ExplosionData buildExplosionData(DetonationEvent event) {
        ExplosionData data = new ExplosionData();
        data.origin.set(event.position);
        data.totalEnergy = event.damage * ENERGY_PER_DAMAGE;
        data.owner = event.owner;
        data.damageType = event.damageType;
        data.blastFraction = event.blastFraction;
        data.thermalFraction = event.thermalFraction;
        data.fragmentFraction = event.fragmentFraction;
        data.isDirectional = event.isDirectional;

        if (event.damageType == DamageType.EMP) {
            data.empRadius = event.areaOfEffect;
        }

        return data;
    }

    float computeOverpressure(ExplosionData data, float distance) {
        float blastEnergy = data.totalEnergy * data.blastFraction;
        float Z = distance / (float) Math.cbrt(blastEnergy);
        if (Z <= 0f) return 0f;
        return BASE_PRESSURE * (0.84f / Z + 3.0f / (Z * Z) + 0.8f / (Z * Z * Z));
    }

    float directionalMultiplier(ExplosionData data, Vector3 dirToTarget) {
        if (!data.isDirectional) return 1f;

        float angle = (float) Math.acos(MathUtils.clamp(dirToTarget.dot(data.directionNormal), -1f, 1f));
        if (angle <= data.coneHalfAngle) {
            return 3f * (1f - angle / data.coneHalfAngle);
        }
        return 0.1f;
    }
}
