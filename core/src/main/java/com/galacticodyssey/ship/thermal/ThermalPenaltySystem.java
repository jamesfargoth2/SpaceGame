package com.galacticodyssey.ship.thermal;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.thermal.events.OverheatWarningEvent;

/**
 * Computes performance penalties when ship subsystems exceed their maximum safe
 * temperatures. Writes throttle cap and fire rate multiplier back to
 * {@link ThermalStateComponent}.
 *
 * <p>Priority 11 -- runs after {@link ThermalSystem} (10) so temperatures are
 * up-to-date before penalties are evaluated.</p>
 */
public class ThermalPenaltySystem extends EntitySystem {

    private static final int PRIORITY = 11;

    private static final ComponentMapper<ThermalStateComponent> thermalMapper =
            ComponentMapper.getFor(ThermalStateComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public ThermalPenaltySystem(EventBus eventBus) {
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

            // --- Engine throttle cap ---
            if (t.engineTemp > t.engineMaxSafeTemp) {
                float overheatRatio = (t.engineTemp - t.engineMaxSafeTemp)
                        / (t.engineDamageTemp - t.engineMaxSafeTemp);
                t.throttleCap = MathUtils.clamp(1f - overheatRatio * 0.9f, 0.1f, 1f);

                eventBus.publish(new OverheatWarningEvent(
                        entity, "engine", t.engineTemp, t.engineMaxSafeTemp));
            } else {
                t.throttleCap = 1f;
            }

            // --- Weapon fire rate multiplier ---
            if (t.weaponBankTemp > t.weaponMaxSafeTemp) {
                // Degrade linearly over 500 K above safe limit, floor at 0.1
                float excess = (t.weaponBankTemp - t.weaponMaxSafeTemp) / 500f;
                t.fireRateMultiplier = MathUtils.clamp(1f - excess * 0.9f, 0.1f, 1f);

                eventBus.publish(new OverheatWarningEvent(
                        entity, "weaponBank", t.weaponBankTemp, t.weaponMaxSafeTemp));
            } else {
                t.fireRateMultiplier = 1f;
            }
        }
    }
}
