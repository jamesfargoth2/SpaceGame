package com.galacticodyssey.core.solar;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.solar.events.CMEBeginEvent;
import com.galacticodyssey.core.solar.events.CMEEndEvent;

/**
 * Manages coronal mass ejection lifecycle on {@link SolarSourceComponent}
 * entities. Counts down active CMEs and publishes begin/end events.
 */
public class CMESystem extends EntitySystem {

    public static final int PRIORITY = 3;

    /** Half-angle of the CME cone (radians). */
    private static final float CME_HALF_ANGLE = MathUtils.degreesToRadians * 30f;

    private static final ComponentMapper<SolarSourceComponent> SOURCE_M =
        ComponentMapper.getFor(SolarSourceComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family SOURCE_FAMILY = Family.all(
        SolarSourceComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private ImmutableArray<Entity> sources;

    private final Pool<Vector3> vectorPool = new Pool<Vector3>() {
        @Override
        protected Vector3 newObject() {
            return new Vector3();
        }
    };

    public CMESystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        sources = engine.getEntitiesFor(SOURCE_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        sources = null;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0, n = sources.size(); i < n; i++) {
            Entity entity = sources.get(i);
            SolarSourceComponent source = SOURCE_M.get(entity);
            if (!source.cmeActive) continue;

            source.cmeRemainingTime -= deltaTime;
            if (source.cmeRemainingTime <= 0f) {
                source.cmeActive = false;
                source.cmeIntensityMultiplier = 1f;
                source.cmeRemainingTime = 0f;
                eventBus.publish(new CMEEndEvent(entity));
            }
        }
    }

    /**
     * Initiates a coronal mass ejection on the given star entity.
     *
     * @param star      entity with {@link SolarSourceComponent}
     * @param duration  CME duration in seconds
     * @param intensity multiplier applied to solar wind pressure
     * @param direction CME cone axis angle in radians (XZ plane)
     */
    public void triggerCME(Entity star, float duration, float intensity, float direction) {
        SolarSourceComponent source = SOURCE_M.get(star);
        if (source == null) return;

        source.cmeActive = true;
        source.cmeIntensityMultiplier = intensity;
        source.cmeDirection = direction;
        source.cmeRemainingTime = duration;
        eventBus.publish(new CMEBeginEvent(star, intensity, duration));
    }

    /**
     * Tests whether a world position lies within the CME cone of a star.
     *
     * @param starEntity entity with {@link SolarSourceComponent} and {@link TransformComponent}
     * @param position   world position to test
     * @return true if inside the 30-degree half-angle cone
     */
    public boolean isInCMECone(Entity starEntity, Vector3 position) {
        SolarSourceComponent source = SOURCE_M.get(starEntity);
        TransformComponent starTransform = TRANSFORM_M.get(starEntity);
        if (source == null || starTransform == null || !source.cmeActive) {
            return false;
        }

        Vector3 toPos = vectorPool.obtain();
        Vector3 cmeDir = vectorPool.obtain();
        try {
            toPos.set(position).sub(starTransform.position).nor();
            cmeDir.set(
                MathUtils.cos(source.cmeDirection), 0f,
                MathUtils.sin(source.cmeDirection)
            );

            float dot = MathUtils.clamp(toPos.dot(cmeDir), -1f, 1f);
            float angle = (float) Math.acos(dot);
            return angle < CME_HALF_ANGLE;
        } finally {
            vectorPool.free(toPos);
            vectorPool.free(cmeDir);
        }
    }
}
