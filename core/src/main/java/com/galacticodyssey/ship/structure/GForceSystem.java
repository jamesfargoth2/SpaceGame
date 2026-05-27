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
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.structure.events.CrewGLocEvent;

/**
 * Computes G-force exposure and accumulates fatigue for entities that have a
 * {@link GForceToleranceComponent}. Publishes {@link CrewGLocEvent} when the
 * entity loses consciousness.
 *
 * <p>Priority 7 -- runs before structural-integrity checks so that G-LOC
 * state is available to other systems in the same frame.</p>
 */
public class GForceSystem extends EntitySystem {

    public static final int PRIORITY = 7;

    private static final float STANDARD_G = 9.81f;
    private static final float FATIGUE_ONSET_G = 3f;
    private static final float FATIGUE_ACCUMULATION_RATE = 0.1f;
    private static final float FATIGUE_RECOVERY_RATE = 0.05f;
    private static final float G_SUIT_MULTIPLIER = 1.5f;

    private static final Family FAMILY = Family.all(
        GForceToleranceComponent.class, PhysicsBodyComponent.class
    ).get();

    private static final ComponentMapper<GForceToleranceComponent> TOLERANCE_M =
        ComponentMapper.getFor(GForceToleranceComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYSICS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final EventBus eventBus;
    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    private ImmutableArray<Entity> entities;

    public GForceSystem(EventBus eventBus) {
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
        GForceToleranceComponent tolerance = TOLERANCE_M.get(entity);
        PhysicsBodyComponent physics = PHYSICS_M.get(entity);
        if (tolerance == null || physics == null || physics.body == null) return;

        // Retrieve linear acceleration from the rigid body's total force / mass
        Vector3 totalForce = vectorPool.obtain();
        try {
            totalForce.set(physics.body.getTotalForce());
            float acceleration = (physics.mass > 0f) ? totalForce.len() / physics.mass : 0f;
            float gForce = acceleration / STANDARD_G;

            float effectiveMaxG = tolerance.maxSustainedG;
            if (tolerance.hasGSuit) {
                effectiveMaxG *= G_SUIT_MULTIPLIER;
            }

            if (gForce > FATIGUE_ONSET_G) {
                float rate = (gForce - FATIGUE_ONSET_G) * FATIGUE_ACCUMULATION_RATE;
                tolerance.gFatigue += rate * dt;
            } else {
                tolerance.gFatigue = Math.max(0f, tolerance.gFatigue - FATIGUE_RECOVERY_RATE * dt);
            }

            if (tolerance.gFatigue > 1f && !tolerance.isIncapacitated) {
                tolerance.isIncapacitated = true;
                eventBus.publish(new CrewGLocEvent(entity, gForce));
            }

            // Allow recovery once fatigue drains back below threshold
            if (tolerance.gFatigue <= 0f && tolerance.isIncapacitated) {
                tolerance.isIncapacitated = false;
            }
        } finally {
            vectorPool.free(totalForce);
        }
    }
}
