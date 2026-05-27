package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.terrain.SurfaceEvents.SeismicEndedEvent;
import com.galacticodyssey.planet.terrain.SurfaceEvents.SeismicStartedEvent;

public class SeismicSystem extends EntitySystem {

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final Array<SeismicEvent> activeQuakes = new Array<>();
    private final EventBus eventBus;
    private ImmutableArray<Entity> surfaceEntities;

    private final Vector3 tmpImpulse = new Vector3();

    public SeismicSystem(EventBus eventBus) {
        super(7);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        surfaceEntities = engine.getEntitiesFor(Family.all(
            GroundVehicleComponent.class,
            TransformComponent.class,
            PhysicsBodyComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        surfaceEntities = null;
    }

    public SeismicEvent triggerQuake(float magnitude, Vector3 epicentre) {
        final float amplitude = (float) (Math.pow(10, magnitude) * 1e-6f);
        final float frequency = MathUtils.random(0.5f, 5f);
        final float duration  = magnitude * 10f;
        SeismicEvent e = new SeismicEvent(epicentre.cpy(), amplitude, frequency, duration);
        activeQuakes.add(e);
        eventBus.publish(new SeismicStartedEvent(e));
        return e;
    }

    @Override
    public void update(float dt) {
        for (int i = activeQuakes.size - 1; i >= 0; i--) {
            SeismicEvent q = activeQuakes.get(i);
            q.elapsedTime += dt;
            applyShakeToSurfaceEntities(q, dt);
            if (q.elapsedTime >= q.duration) {
                activeQuakes.removeIndex(i);
                eventBus.publish(new SeismicEndedEvent(q));
            }
        }
    }

    private void applyShakeToSurfaceEntities(SeismicEvent quake, float dt) {
        if (surfaceEntities == null) return;
        for (int i = 0; i < surfaceEntities.size(); i++) {
            final Entity entity = surfaceEntities.get(i);
            final TransformComponent transform = transformMapper.get(entity);
            final PhysicsBodyComponent physics = physicsMapper.get(entity);
            if (physics.body == null) continue;

            final float dist = transform.position.dst(quake.epicentre);
            final float decay = 1f / (1f + dist * 0.01f);
            final float displacement = quake.amplitude * decay
                * MathUtils.sin(quake.elapsedTime * quake.frequency * MathUtils.PI2);

            // Apply as impulse in the vertical (Y) direction for both position shake and velocity
            final float impulseMag = physics.mass * displacement / Math.max(dt, 0.001f);
            tmpImpulse.set(0f, impulseMag, 0f);
            physics.body.applyCentralImpulse(tmpImpulse);
            physics.body.activate();

            // Direct position displacement for visual fidelity
            transform.position.y += displacement;
        }
    }

    public Array<SeismicEvent> getActiveQuakes() {
        return activeQuakes;
    }
}
