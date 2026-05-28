package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.events.StatusEffectAppliedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.ExtinguishedEvent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ObjectConsumedByFireEvent;
import com.galacticodyssey.planet.thermal.events.ThawEvent;

/**
 * Owns the burning / frozen lifecycle of entities. Subscribes to ignition/freeze/thaw events
 * from {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} and manages
 * {@link BurningComponent} / {@link FrozenComponent}. Each tick, burning entities deposit
 * their heat output into their own (and neighbours') {@code incomingHeat}, burn fuel down,
 * apply the BURNING status to entities with health, request ground ignition under them, and
 * are consumed (removed or charred) when fuel exhausts.
 *
 * <p>Priority 30 -- deposits heat before {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} (31).</p>
 */
public class CombustionSystem extends EntitySystem {

    public static final int PRIORITY = 30;
    public static final float SPREAD_RADIUS = 3f;      // m -- fire-to-fire heating range
    public static final float SPREAD_FRACTION = 0.25f; // fraction of heatOutput shed to neighbours
    public static final float GROUND_IGNITE_STRENGTH = 0.5f;

    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BurningComponent> BURN_M =
            ComponentMapper.getFor(BurningComponent.class);
    private static final ComponentMapper<StatusEffectsComponent> STATUS_M =
            ComponentMapper.getFor(StatusEffectsComponent.class);

    private final EventBus eventBus;
    private final ThermalMaterialRegistry registry;
    private ImmutableArray<Entity> burning;
    private ImmutableArray<Entity> allTemp;

    public CombustionSystem(EventBus eventBus, ThermalMaterialRegistry registry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        eventBus.subscribe(IgnitionEvent.class, this::onIgnition);
        eventBus.subscribe(FreezeEvent.class, this::onFreeze);
        eventBus.subscribe(ThawEvent.class, this::onThaw);
    }

    @Override
    public void addedToEngine(Engine engine) {
        burning = engine.getEntitiesFor(
                Family.all(BurningComponent.class, TemperatureComponent.class, TransformComponent.class).get());
        allTemp = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    private void onIgnition(IgnitionEvent ev) {
        Entity e = ev.entity;
        if (BURN_M.get(e) != null) return;
        TemperatureComponent t = TEMP_M.get(e);
        ThermalMaterial m = (t != null) ? t.material : null;
        BurningComponent b = new BurningComponent();
        if (m != null) {
            b.heatOutput = m.burnHeatOutput;
            b.fuelRemaining = m.combustionEnergy;
        }
        e.add(b);

        if (STATUS_M.get(e) != null && !b.statusApplied) {
            eventBus.publish(new StatusEffectAppliedEvent(e, StatusEffectType.BURNING, null));
            b.statusApplied = true;
        }
    }

    private void onFreeze(FreezeEvent ev) {
        Entity e = ev.entity;
        TemperatureComponent t = TEMP_M.get(e);
        ThermalMaterial m = (t != null) ? t.material : null;
        FrozenComponent f = new FrozenComponent();
        if (m != null) {
            f.speedMultiplier = m.frozenSpeedMultiplier;
            f.brittle = m.brittleWhenFrozen;
        }
        e.add(f);
    }

    private void onThaw(ThawEvent ev) {
        ev.entity.remove(FrozenComponent.class);
    }

    @Override
    public void update(float dt) {
        for (int i = burning.size() - 1; i >= 0; i--) {
            Entity e = burning.get(i);
            BurningComponent b = BURN_M.get(e);
            TemperatureComponent t = TEMP_M.get(e);

            // Self-heating keeps the fire hot (deposited before integration at priority 31).
            t.incomingHeat += b.heatOutput * b.intensity;

            // Spread heat to nearby flammable/normal objects.
            spreadHeat(e, b, dt);

            // Ignite the ground/fuel grid beneath the burning entity.
            eventBus.publish(new IgniteAtEvent(
                    TRANSFORM_M.get(e).position.x, TRANSFORM_M.get(e).position.z, GROUND_IGNITE_STRENGTH));

            // Burn fuel.
            b.fuelRemaining -= b.heatOutput * b.intensity * dt;
            if (b.fuelRemaining <= 0f) {
                consume(e, t);
            }
        }
    }

    private void spreadHeat(Entity source, BurningComponent b, float dt) {
        com.badlogic.gdx.math.Vector3 srcPos = TRANSFORM_M.get(source).position;
        for (int j = 0; j < allTemp.size(); j++) {
            Entity other = allTemp.get(j);
            if (other == source) continue;
            float dist = TRANSFORM_M.get(other).position.dst(srcPos);
            if (dist > SPREAD_RADIUS || dist <= 0f) continue;
            float falloff = 1f - (dist / SPREAD_RADIUS);
            TEMP_M.get(other).incomingHeat += b.heatOutput * SPREAD_FRACTION * falloff;
        }
    }

    private void consume(Entity e, TemperatureComponent t) {
        ThermalMaterial m = t.material;
        e.remove(BurningComponent.class);
        t.state = ThermalState.NORMAL;
        eventBus.publish(new ExtinguishedEvent(e));

        if (m != null && m.consumedWhenBurnt) {
            String charId = m.charMaterialId;
            eventBus.publish(new ObjectConsumedByFireEvent(e, charId));
            if (charId != null) {
                t.materialId = charId;
                t.material = registry.get(charId);
            } else {
                getEngine().removeEntity(e);
            }
        }
    }
}
