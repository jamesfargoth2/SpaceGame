package com.galacticodyssey.ship.structure;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.structure.events.VentForceEvent;

/**
 * For each breached {@link StructuralZone} that still has interior pressure,
 * computes the mass flow rate of escaping atmosphere using a choked / subsonic
 * isentropic flow model (critical pressure ratio 0.528 for diatomic gas).
 *
 * <ul>
 *     <li>Reduces zone interior pressure proportionally to the mass lost.</li>
 *     <li>Publishes a {@link VentForceEvent} representing the thrust-like
 *         force the escaping gas exerts on the ship.</li>
 * </ul>
 *
 * <p>Priority 9 -- runs after {@link StructuralIntegritySystem} so that freshly
 * breached zones are processed in the same frame.</p>
 */
public class AtmosphereVentSystem extends EntitySystem {

    public static final int PRIORITY = 9;

    /** Specific gas constant for air (J / (kg * K)). */
    private static final float R_AIR = 287f;

    /** Heat-capacity ratio for diatomic gas (N2 / O2 air). */
    private static final float GAMMA = 1.4f;

    /** Critical pressure ratio for choked flow with gamma = 1.4. */
    private static final float CRITICAL_RATIO = 0.528f;

    /** Assumed interior temperature (K). Roughly room temp. */
    private static final float INTERIOR_TEMP = 293f;

    /** Exterior pressure (Pa). Space vacuum. */
    private static final float EXTERIOR_PRESSURE = 0f;

    private static final Family FAMILY = Family.all(
        StructuralIntegrityComponent.class
    ).get();

    private static final ComponentMapper<StructuralIntegrityComponent> STRUCT_M =
        ComponentMapper.getFor(StructuralIntegrityComponent.class);

    private final EventBus eventBus;
    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private ImmutableArray<Entity> entities;

    public AtmosphereVentSystem(EventBus eventBus) {
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
            processEntity(entities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float dt) {
        StructuralIntegrityComponent structure = STRUCT_M.get(entity);
        if (structure == null) return;

        for (int z = 0, zn = structure.zones.size; z < zn; z++) {
            StructuralZone zone = structure.zones.get(z);
            if (!zone.isBreached) continue;
            if (zone.pressure <= EXTERIOR_PRESSURE) continue;
            if (zone.breachArea <= 0f) continue;

            float massFlowRate = computeMassFlowRate(zone.pressure, zone.breachArea);
            float massLost = massFlowRate * dt;

            // Update interior pressure from ideal gas: P = rho * R * T
            float rho = zone.pressure / (R_AIR * INTERIOR_TEMP);
            if (zone.volume > 0f) {
                float newRho = Math.max(0f, rho - massLost / zone.volume);
                zone.pressure = Math.max(EXTERIOR_PRESSURE, newRho * R_AIR * INTERIOR_TEMP);
            }

            // Vent thrust: F = mdot * v_exit
            float speedOfSound = (float) Math.sqrt(GAMMA * R_AIR * INTERIOR_TEMP);
            float ventForce = massFlowRate * speedOfSound;

            if (ventForce > 0f) {
                Vector3 ventDir = vectorPool.obtain();
                try {
                    // Force pushes the ship opposite to the breach normal
                    ventDir.set(zone.breachNormal).scl(-1f);
                    eventBus.publish(new VentForceEvent(entity, ventDir, ventForce));
                } finally {
                    vectorPool.free(ventDir);
                }
            }
        }
    }

    /**
     * Computes the mass flow rate (kg/s) of atmosphere escaping through a breach
     * into vacuum, using an isentropic choked-flow model.
     */
    float computeMassFlowRate(float interiorPressure, float breachArea) {
        if (interiorPressure <= EXTERIOR_PRESSURE) return 0f;

        float pressureRatio = EXTERIOR_PRESSURE / interiorPressure;

        float mach;
        if (pressureRatio <= CRITICAL_RATIO) {
            // Choked flow -- exits at Mach 1
            mach = 1f;
        } else {
            // Subsonic expansion
            float exponent = -(GAMMA - 1f) / GAMMA;
            mach = (float) Math.sqrt(
                (2f / (GAMMA - 1f)) * (Math.pow(pressureRatio, exponent) - 1f)
            );
        }

        float speedOfSound = (float) Math.sqrt(GAMMA * R_AIR * INTERIOR_TEMP);
        float exitVelocity = mach * speedOfSound;
        float exitDensity = interiorPressure / (R_AIR * INTERIOR_TEMP);

        return exitDensity * exitVelocity * breachArea;
    }
}
