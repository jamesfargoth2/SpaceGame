package com.galacticodyssey.data.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.GalacticAssetManager;
import com.galacticodyssey.data.components.StreamableComponent;

public final class StreamingSystem extends IteratingSystem {

    private static final ComponentMapper<StreamableComponent> STREAM_MAP =
        ComponentMapper.getFor(StreamableComponent.class);
    private static final ComponentMapper<TransformComponent> XFORM_MAP =
        ComponentMapper.getFor(TransformComponent.class);

    private final GalacticAssetManager assetManager;
    private final Vector3 cameraPosition = new Vector3();

    public StreamingSystem(GalacticAssetManager assetManager) {
        super(Family.all(StreamableComponent.class, TransformComponent.class).get());
        this.assetManager = assetManager;
    }

    public void setCameraPosition(Vector3 position) {
        cameraPosition.set(position);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StreamableComponent sc = STREAM_MAP.get(entity);
        TransformComponent xform = XFORM_MAP.get(entity);

        float distance = cameraPosition.dst(xform.position);
        GalacticAssetManager.StreamingRadius radii = assetManager.getStreamingRadius(sc.category);

        if (distance < radii.prefetchRadius()) {
            if (sc.handle == null) {
                sc.handle = assetManager.enqueue(sc.assetId, sc.category, distance);
            }
        } else if (distance > radii.unloadRadius() && sc.handle != null) {
            sc.handle.release();
            sc.handle = null;
        }
    }
}
