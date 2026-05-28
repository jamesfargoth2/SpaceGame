package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ThawEvent;

/**
 * Integrates per-object temperature from accumulated {@code incomingHeat} plus radiative
 * and conductive exchange with the {@link ThermalEnvironment}, then emits ignition / freeze
 * / thaw transition events. Resets {@code incomingHeat} after integrating.
 *
 * <p>Priority 31 -- runs after all heat-deposition systems (28-30). See plan ordering table.</p>
 */
public class ObjectTemperatureSystem extends EntitySystem {

    public static final int PRIORITY = 31;
    public static final float O2_MIN_COMBUSTION = 0.10f;
    public static final float THAW_HYSTERESIS = 2f;

    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;
    private final ThermalMaterialRegistry registry;
    private final ThermalEnvironment environment;
    private ImmutableArray<Entity> entities;

    public ObjectTemperatureSystem(EventBus eventBus, ThermalMaterialRegistry registry,
                                   ThermalEnvironment environment) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        this.environment = environment;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            TemperatureComponent t = TEMP_M.get(e);
            TransformComponent tr = TRANSFORM_M.get(e);
            resolveMaterial(t);

            Vector3 pos = tr.position;
            float ambient = environment.ambientTemp(pos);
            float emissivity = (t.material != null) ? t.material.emissivity : 0.85f;

            float qOut = ThermalMath.radiativeCooling(t.temperature, ambient, t.surfaceArea, emissivity)
                       + ThermalMath.conduction(t.temperature, ambient, t.surfaceArea);
            float netWatts = t.incomingHeat - qOut;
            if (t.thermalMass > 0f) {
                t.temperature += (netWatts / t.thermalMass) * dt;
            }
            t.incomingHeat = 0f;

            checkTransitions(e, t, pos);
        }
    }

    private void resolveMaterial(TemperatureComponent t) {
        if (t.material == null && t.materialId != null) {
            t.material = registry.get(t.materialId);
        }
    }

    private void checkTransitions(Entity e, TemperatureComponent t, Vector3 pos) {
        ThermalMaterial m = t.material;
        if (m == null) return;

        if (t.state == ThermalState.NORMAL) {
            if (m.flammable && t.temperature >= m.ignitionPoint
                    && environment.oxygenFraction(pos) >= O2_MIN_COMBUSTION) {
                t.state = ThermalState.BURNING;
                eventBus.publish(new IgnitionEvent(e));
            } else if (m.freezable && t.temperature <= m.freezePoint) {
                t.state = ThermalState.FROZEN;
                eventBus.publish(new FreezeEvent(e));
            }
        } else if (t.state == ThermalState.FROZEN
                && t.temperature > m.freezePoint + THAW_HYSTERESIS) {
            t.state = ThermalState.NORMAL;
            eventBus.publish(new ThawEvent(e));
        }
    }
}
