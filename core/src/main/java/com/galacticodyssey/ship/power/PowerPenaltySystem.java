package com.galacticodyssey.ship.power;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.ship.thermal.ThermalStateComponent;

/**
 * Merges power allocation ratios with thermal penalties into final subsystem
 * performance multipliers. The effective multiplier is the minimum of the
 * thermal penalty and the power allocation ratio.
 *
 * <p>Priority 12 — runs after ThermalPenaltySystem (11) so both thermal and
 * power data are up-to-date.</p>
 */
public class PowerPenaltySystem extends EntitySystem {

    private static final int PRIORITY = 12;

    private static final ComponentMapper<PowerStateComponent> powerMapper =
            ComponentMapper.getFor(PowerStateComponent.class);
    private static final ComponentMapper<ThermalStateComponent> thermalMapper =
            ComponentMapper.getFor(ThermalStateComponent.class);

    private ImmutableArray<Entity> entities;

    public PowerPenaltySystem() {
        super(PRIORITY);
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(PowerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            PowerStateComponent power = powerMapper.get(entity);
            ThermalStateComponent thermal = thermalMapper.get(entity);

            if (thermal != null) {
                thermal.throttleCap = Math.min(thermal.throttleCap, power.engineAllocation);
                thermal.fireRateMultiplier = Math.min(thermal.fireRateMultiplier, power.weaponAllocation);
            }
        }
    }
}
