package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;

/**
 * Dev-only helper: generate a random creature and spawn it a few metres in front of a position.
 * Returns the entity; its render ModelInstances are stored on the CreatureRenderComponent for the
 * render system to draw.
 */
public final class FaunaDebugSpawner {
    private final CreatureGenerator generator;
    private final CreatureMeshBuilder meshBuilder;
    private long nextSeed = 1L;

    public FaunaDebugSpawner(CreatureGenerator generator, CreatureMeshBuilder meshBuilder) {
        this.generator = generator;
        this.meshBuilder = meshBuilder;
    }

    /** Spawns in front of {@code origin} along {@code forward} (xz-plane), at origin height. */
    public Entity spawnInFront(Engine engine, Vector3 origin, Vector3 forward, float distance) {
        long seed = nextSeed++;
        CreatureSpec spec = generator.generate(seed);   // seeded archetype pick
        Vector3 flat = new Vector3(forward.x, 0f, forward.z).nor().scl(distance);
        Vector3 pos = new Vector3(origin).add(flat);
        Entity e = new CreatureFactory().create(engine, spec, pos);

        Array<ModelInstance> instances = meshBuilder.build(spec);
        for (ModelInstance inst : instances) inst.transform.translate(pos);
        CreatureRenderComponent render = e.getComponent(CreatureRenderComponent.class);
        render.modelInstance = instances;   // render system iterates and draws
        return e;
    }
}
