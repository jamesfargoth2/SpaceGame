package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.OrbitalConstants;

public class OrbitalPositionSystem extends EntitySystem {

    private static final ComponentMapper<OrbitalBodyComponent> orbitalMapper =
        ComponentMapper.getFor(OrbitalBodyComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> bodies;

    public OrbitalPositionSystem() {
        super(3);
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
            Family.all(OrbitalBodyComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (bodies == null) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int pass = 0; pass < 3; pass++) {
                CelestialBodyType targetType = CelestialBodyType.values()[pass];
                for (int i = 0; i < bodies.size(); i++) {
                    Entity entity = bodies.get(i);
                    OrbitalBodyComponent orbital = orbitalMapper.get(entity);
                    if (orbital.bodyType != targetType) continue;

                    if (orbital.orbitalSlot == null) continue;

                    TransformComponent transform = transformMapper.get(entity);
                    orbital.orbitalSlot.getLocalPosition(tmp);
                    tmp.scl(OrbitalConstants.AU_TO_GAME_UNITS);

                    if (orbital.parentBody != null) {
                        TransformComponent parentTransform = transformMapper.get(orbital.parentBody);
                        if (parentTransform != null) {
                            tmp.add(parentTransform.position);
                        }
                    }

                    transform.position.set(tmp);
                }
            }
        } finally {
            Pools.free(tmp);
        }
    }
}
