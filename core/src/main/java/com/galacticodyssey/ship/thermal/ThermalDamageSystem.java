package com.galacticodyssey.ship.thermal;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.thermal.events.ThermalDamageEvent;

/**
 * Applies continuous thermal damage when ship subsystems exceed their damage
 * thresholds. Damage rate scales linearly with temperature excess.
 *
 * <p>Priority 12 -- runs after {@link ThermalPenaltySystem} (11).</p>
 */
public class ThermalDamageSystem extends EntitySystem {

    private static final int PRIORITY = 12;

    /** Damage per Kelvin of excess per second for engines. */
    private static final float ENGINE_DAMAGE_COEFF = 0.001f;

    /** Damage per Kelvin of excess per second for hull. */
    private static final float HULL_DAMAGE_COEFF = 0.0005f;

    private static final ComponentMapper<ThermalStateComponent> thermalMapper =
            ComponentMapper.getFor(ThermalStateComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public ThermalDamageSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(ThermalStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            ThermalStateComponent t = thermalMapper.get(entity);

            // Engine thermal damage
            if (t.engineTemp > t.engineDamageTemp) {
                float excess = t.engineTemp - t.engineDamageTemp;
                float damage = excess * ENGINE_DAMAGE_COEFF * deltaTime;
                eventBus.publish(new ThermalDamageEvent(entity, "engine", damage));
            }

            // Hull thermal damage
            if (t.hullTemp > t.hullMaxSafeTemp) {
                float excess = t.hullTemp - t.hullMaxSafeTemp;
                float damage = excess * HULL_DAMAGE_COEFF * deltaTime;
                eventBus.publish(new ThermalDamageEvent(entity, "hull", damage));
            }
        }
    }
}
