package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Deposits watts from {@link HeatSourceComponent} emitters into the {@code incomingHeat}
 * accumulator of nearby {@link TemperatureComponent} entities, with linear distance falloff
 * (and optional cone gating). Priority 29 -- before {@link ObjectTemperatureSystem} (31).
 */
public class HeatSourceSystem extends EntitySystem {

    public static final int PRIORITY = 29;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HeatSourceComponent> SOURCE_M =
            ComponentMapper.getFor(HeatSourceComponent.class);
    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);

    private ImmutableArray<Entity> emitters;
    private ImmutableArray<Entity> targets;

    private final Vector3 toTarget = new Vector3();

    public HeatSourceSystem() { super(PRIORITY); }

    @Override
    public void addedToEngine(Engine engine) {
        emitters = engine.getEntitiesFor(
                Family.all(HeatSourceComponent.class, TransformComponent.class).get());
        targets = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < emitters.size(); i++) {
            Entity emitterEntity = emitters.get(i);
            HeatSourceComponent src = SOURCE_M.get(emitterEntity);

            if (src.lifetime >= 0f) {
                src.lifetime -= dt;
                if (src.lifetime < 0f) {
                    emitterEntity.remove(HeatSourceComponent.class);
                    continue;
                }
            }
            Vector3 origin = TRANSFORM_M.get(emitterEntity).position;
            depositToTargets(src, origin);
        }
    }

    private void depositToTargets(HeatSourceComponent src, Vector3 origin) {
        for (int j = 0; j < targets.size(); j++) {
            Entity tgtEntity = targets.get(j);
            TransformComponent tgtTr = TRANSFORM_M.get(tgtEntity);
            toTarget.set(tgtTr.position).sub(origin);
            float dist = toTarget.len();
            if (dist > src.radius || dist <= 0f) continue;

            if (src.cone) {
                float angle = (float) Math.acos(
                        MathUtils.clamp(toTarget.nor().dot(src.direction), -1f, 1f));
                if (angle > src.coneHalfAngleRad) continue;
            }
            float falloff = 1f - (dist / src.radius); // linear: full at source, 0 at edge
            TEMP_M.get(tgtEntity).incomingHeat += src.power * falloff;
        }
    }
}
