package com.galacticodyssey.ship.lifesupport;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.lifesupport.events.CrewHealthAlertEvent;
import com.galacticodyssey.ship.lifesupport.events.CrewHealthAlertEvent.AlertType;

/**
 * Evaluates crew health based on compartment atmosphere conditions and publishes
 * {@link CrewHealthAlertEvent} when dangerous thresholds are crossed.
 *
 * <p>Thresholds:
 * <ul>
 *   <li>Hypoxia: O2 partial pressure &lt; 16 kPa</li>
 *   <li>CO2 toxicity: CO2 partial pressure &gt; 1 kPa</li>
 *   <li>Decompression: total pressure &lt; 20 kPa</li>
 * </ul>
 *
 * <p>Severity accumulators are tracked on the component and recover slowly when
 * conditions return to safe ranges.
 */
public class AtmosphereHealthSystem extends EntitySystem {

    public static final int PRIORITY = 12;

    // Thresholds (kPa)
    private static final float HYPOXIA_THRESHOLD = 16.0f;
    private static final float CO2_TOXIC_THRESHOLD = 1.0f;
    private static final float DECOMPRESSION_THRESHOLD = 20.0f;

    // Recovery rate when conditions are safe (severity/s)
    private static final float RECOVERY_RATE = 0.1f;
    private static final float CO2_RECOVERY_RATE = 0.05f;

    // Severity at which a health alert is published
    private static final float ALERT_SEVERITY = 1.0f;

    private static final Family FAMILY = Family.all(
        CompartmentAtmosphereComponent.class
    ).get();

    private static final ComponentMapper<CompartmentAtmosphereComponent> ATMO_M =
        ComponentMapper.getFor(CompartmentAtmosphereComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    public AtmosphereHealthSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (entities == null) return;

        for (int i = 0, n = entities.size(); i < n; i++) {
            Entity entity = entities.get(i);
            CompartmentAtmosphereComponent atmo = ATMO_M.get(entity);
            if (atmo.crewCount <= 0) continue;

            evaluateHypoxia(entity, atmo, deltaTime);
            evaluateCO2Toxicity(entity, atmo, deltaTime);
            evaluateDecompression(entity, atmo, deltaTime);
        }
    }

    private void evaluateHypoxia(Entity entity, CompartmentAtmosphereComponent atmo, float dt) {
        if (atmo.o2Pressure < HYPOXIA_THRESHOLD) {
            float severity = (HYPOXIA_THRESHOLD - atmo.o2Pressure) / HYPOXIA_THRESHOLD;
            atmo.hypoxiaSeverity += severity * dt;
            if (atmo.hypoxiaSeverity >= ALERT_SEVERITY) {
                eventBus.publish(new CrewHealthAlertEvent(entity, AlertType.HYPOXIA, atmo.hypoxiaSeverity));
            }
        } else {
            atmo.hypoxiaSeverity = Math.max(0f, atmo.hypoxiaSeverity - RECOVERY_RATE * dt);
        }
    }

    private void evaluateCO2Toxicity(Entity entity, CompartmentAtmosphereComponent atmo, float dt) {
        if (atmo.co2Pressure > CO2_TOXIC_THRESHOLD) {
            float severity = (atmo.co2Pressure - CO2_TOXIC_THRESHOLD) / 3.0f;
            atmo.co2ToxicitySeverity += severity * dt;
            if (atmo.co2ToxicitySeverity >= ALERT_SEVERITY) {
                eventBus.publish(new CrewHealthAlertEvent(entity, AlertType.CO2_TOXIC, atmo.co2ToxicitySeverity));
            }
        } else {
            atmo.co2ToxicitySeverity = Math.max(0f, atmo.co2ToxicitySeverity - CO2_RECOVERY_RATE * dt);
        }
    }

    private void evaluateDecompression(Entity entity, CompartmentAtmosphereComponent atmo, float dt) {
        if (atmo.totalPressure < DECOMPRESSION_THRESHOLD) {
            float severity = (DECOMPRESSION_THRESHOLD - atmo.totalPressure) / DECOMPRESSION_THRESHOLD;
            atmo.decompressionSeverity += severity * dt;
            if (atmo.decompressionSeverity >= ALERT_SEVERITY) {
                eventBus.publish(new CrewHealthAlertEvent(entity, AlertType.DECOMPRESSION, atmo.decompressionSeverity));
            }
        } else {
            atmo.decompressionSeverity = Math.max(0f, atmo.decompressionSeverity - RECOVERY_RATE * dt);
        }
    }
}
