package com.galacticodyssey.core.blackhole;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.blackhole.events.EventHorizonCrossedEvent;
import com.galacticodyssey.core.blackhole.events.ISCOWarningEvent;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Checks every entity with a {@link TransformComponent} against all black holes
 * and enforces the event horizon boundary.
 *
 * <ul>
 *   <li><strong>r <= schwarzschildRadius:</strong> publishes
 *       {@link EventHorizonCrossedEvent}. Crossing is irreversible; the entity
 *       must be destroyed. No bounce or repulsion is applied.</li>
 *   <li><strong>r <= innerStableOrbit (ISCO):</strong> sets
 *       {@link TimeDilationComponent#isInsideISCO} and publishes
 *       {@link ISCOWarningEvent}. The entity can still escape with thrust.</li>
 * </ul>
 *
 * <p>Priority 1 (highest) so that horizon checks run before tidal forces and
 * time dilation to avoid applying physics to already-destroyed entities.
 */
public class EventHorizonSystem extends EntitySystem {

    public static final int PRIORITY = 1;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BlackHoleComponent> BLACK_HOLE_M =
        ComponentMapper.getFor(BlackHoleComponent.class);
    private static final ComponentMapper<TimeDilationComponent> DILATION_M =
        ComponentMapper.getFor(TimeDilationComponent.class);

    private static final Family BLACK_HOLE_FAMILY = Family.all(
        TransformComponent.class, BlackHoleComponent.class
    ).get();

    private static final Family BODY_FAMILY = Family.all(
        TransformComponent.class
    ).get();

    private final EventBus eventBus;

    private ImmutableArray<Entity> blackHoles;
    private ImmutableArray<Entity> bodies;

    public EventHorizonSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        blackHoles = engine.getEntitiesFor(BLACK_HOLE_FAMILY);
        bodies = engine.getEntitiesFor(BODY_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        blackHoles = null;
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (blackHoles.size() == 0) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < bodies.size(); i++) {
                Entity body = bodies.get(i);

                // Black hole entities don't check against themselves
                if (BLACK_HOLE_M.has(body)) continue;

                TransformComponent bodyTransform = TRANSFORM_M.get(body);

                for (int j = 0; j < blackHoles.size(); j++) {
                    Entity bhEntity = blackHoles.get(j);
                    BlackHoleComponent bh = BLACK_HOLE_M.get(bhEntity);
                    TransformComponent bhTransform = TRANSFORM_M.get(bhEntity);

                    tmp.set(bodyTransform.position).sub(bhTransform.position);
                    float r = tmp.len();

                    if (r <= bh.schwarzschildRadius) {
                        // Irreversible destruction - no bounce, no repulsion
                        eventBus.publish(new EventHorizonCrossedEvent(body));
                        break; // Entity is destroyed, skip remaining black holes
                    }

                    if (r <= bh.innerStableOrbit) {
                        // Inside ISCO - mark flag and warn
                        TimeDilationComponent dilation = DILATION_M.get(body);
                        if (dilation != null) {
                            dilation.isInsideISCO = true;
                        }
                        float distanceToHorizon = r - bh.schwarzschildRadius;
                        eventBus.publish(new ISCOWarningEvent(body, distanceToHorizon));
                    }
                }
            }
        } finally {
            Pools.free(tmp);
        }
    }
}
