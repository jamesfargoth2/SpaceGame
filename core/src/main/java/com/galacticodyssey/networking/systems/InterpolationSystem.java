package com.galacticodyssey.networking.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.networking.components.InterpolationComponent;
import com.galacticodyssey.networking.interpolation.EntitySnapshot;
import com.galacticodyssey.networking.interpolation.SnapshotBuffer;

/**
 * Smooths remote entity positions by interpolating between buffered server snapshots.
 *
 * <p>Runs at priority 55 — after game logic systems but before rendering. For each entity
 * with both {@link InterpolationComponent} and {@link TransformComponent}:
 * <ol>
 *   <li>Computes the target render tick = newestTick − {@link InterpolationComponent#RENDER_DELAY_TICKS}.</li>
 *   <li>Looks for bracketing snapshots in the buffer and linearly interpolates between them.</li>
 *   <li>Falls back to linear extrapolation (capped at {@link InterpolationComponent#MAX_EXTRAPOLATION_SECONDS})
 *       when no bracket is found.</li>
 *   <li>Freezes the entity at its last known position once the extrapolation budget is exhausted.</li>
 *   <li>Blends smoothly over {@link InterpolationComponent#BLEND_FRAMES} frames when new data arrives
 *       after a freeze.</li>
 * </ol>
 */
public class InterpolationSystem extends EntitySystem {

    private final float tickInterval;
    private volatile int currentServerTick;

    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<InterpolationComponent> interpMapper =
            ComponentMapper.getFor(InterpolationComponent.class);

    private ImmutableArray<Entity> remoteEntities;

    /**
     * @param tickInterval seconds per server tick (e.g. 0.05f for 20 Hz)
     */
    public InterpolationSystem(float tickInterval) {
        super(55);
        this.tickInterval = tickInterval;
    }

    /** Called by the network layer each time a new authoritative tick number is received. */
    public void setCurrentServerTick(int tick) {
        this.currentServerTick = tick;
    }

    @Override
    public void addedToEngine(Engine engine) {
        remoteEntities = engine.getEntitiesFor(
                Family.all(TransformComponent.class, InterpolationComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < remoteEntities.size(); i++) {
            processEntity(remoteEntities.get(i), deltaTime);
        }
    }

    private void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        InterpolationComponent interp = interpMapper.get(entity);
        SnapshotBuffer buffer = interp.getSnapshotBuffer();

        if (buffer.size() == 0) return;

        // Frozen — waiting for new data; blend will restart once a packet arrives.
        if (interp.frozen) return;

        int targetTick = currentServerTick - InterpolationComponent.RENDER_DELAY_TICKS;
        EntitySnapshot[] pair = buffer.findBracketing(targetTick);

        if (pair != null) {
            // Normal interpolation path.
            interp.extrapolationTimer = 0;

            int tickSpan = pair[1].tick - pair[0].tick;
            float t = tickSpan > 0 ? (float) (targetTick - pair[0].tick) / tickSpan : 0f;
            t = Math.max(0f, Math.min(1f, t));

            EntitySnapshot lerped = EntitySnapshot.lerp(pair[0], pair[1], t);

            if (interp.blendFramesRemaining > 0) {
                // Blend from the freeze position to the interpolated position.
                float blendT = 1f - (float) interp.blendFramesRemaining / InterpolationComponent.BLEND_FRAMES;
                transform.position.set(
                        interp.blendFromX + (lerped.posX - interp.blendFromX) * blendT,
                        interp.blendFromY + (lerped.posY - interp.blendFromY) * blendT,
                        interp.blendFromZ + (lerped.posZ - interp.blendFromZ) * blendT
                );
                // Blend rotation with normalization to preserve unit quaternion.
                float rx = interp.blendFromRotX + (lerped.rotX - interp.blendFromRotX) * blendT;
                float ry = interp.blendFromRotY + (lerped.rotY - interp.blendFromRotY) * blendT;
                float rz = interp.blendFromRotZ + (lerped.rotZ - interp.blendFromRotZ) * blendT;
                float rw = interp.blendFromRotW + (lerped.rotW - interp.blendFromRotW) * blendT;
                float invLen = 1f / (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
                transform.rotation.set(rx * invLen, ry * invLen, rz * invLen, rw * invLen);
                interp.blendFramesRemaining--;
            } else {
                transform.position.set(lerped.posX, lerped.posY, lerped.posZ);
                transform.rotation.set(lerped.rotX, lerped.rotY, lerped.rotZ, lerped.rotW);
            }
        } else {
            // No bracketing pair — extrapolate from the newest snapshot.
            EntitySnapshot newest = buffer.getNewest();
            if (newest == null) return;

            interp.extrapolationTimer += deltaTime;
            if (interp.extrapolationTimer >= InterpolationComponent.MAX_EXTRAPOLATION_SECONDS) {
                interp.frozen = true;
                return;
            }
            EntitySnapshot extrapolated = newest.extrapolate(interp.extrapolationTimer, tickInterval);
            transform.position.set(extrapolated.posX, extrapolated.posY, extrapolated.posZ);
            transform.rotation.set(extrapolated.rotX, extrapolated.rotY, extrapolated.rotZ, extrapolated.rotW);
        }
    }
}
