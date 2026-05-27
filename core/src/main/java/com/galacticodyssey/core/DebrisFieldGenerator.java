package com.galacticodyssey.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.core.components.DebrisComponent;
import com.galacticodyssey.core.components.DebrisComponent.DebrisClass;
import com.galacticodyssey.core.components.TransformComponent;

import java.util.Random;

public final class DebrisFieldGenerator {

    private static final float DENSITY = 2500f;
    private static final float FOUR_THIRDS_PI = (4f / 3f) * MathUtils.PI;

    private DebrisFieldGenerator() {}

    public static Entity createAsteroid(Engine engine, Vector3 position, float mass, float radius) {
        Entity entity = engine.createEntity();

        TransformComponent transform = engine.createComponent(TransformComponent.class);
        transform.position.set(position);
        entity.add(transform);

        DebrisComponent debris = engine.createComponent(DebrisComponent.class);
        debris.mass = mass;
        debris.radius = radius;
        debris.debrisClass = classifyByRadius(radius);
        debris.fractureEnergy = mass * 10f;

        float inertia = 0.4f * mass * radius * radius;
        debris.inertiaTensor.set(inertia, inertia, inertia);

        debris.angularVelocity.set(
            MathUtils.random(-0.05f, 0.05f),
            MathUtils.random(-0.05f, 0.05f),
            MathUtils.random(-0.05f, 0.05f)
        );

        entity.add(debris);
        engine.addEntity(entity);
        return entity;
    }

    public static Array<Entity> generateBelt(Engine engine, float innerRadius, float outerRadius,
                                              float beltInclination, int count, long seed) {
        Array<Entity> entities = new Array<>(count);
        Random rng = new Random(seed);
        float baseMass = 500f;

        for (int i = 0; i < count; i++) {
            float r = innerRadius + (outerRadius - innerRadius) * rng.nextFloat();
            float angle = rng.nextFloat() * MathUtils.PI2;
            float incOffset = (rng.nextFloat() - 0.5f) * 2f * beltInclination;

            float x = r * MathUtils.cos(angle);
            float y = r * MathUtils.sin(incOffset);
            float z = r * MathUtils.sin(angle);

            float rawRandom = rng.nextFloat();
            float clamped = Math.max(rawRandom, 0.01f);
            float mass = baseMass * (1f / (clamped * clamped));
            mass = MathUtils.clamp(mass, 10f, 1e9f);

            float radius = (float) Math.cbrt(3.0 * mass / (4.0 * Math.PI * DENSITY));

            Vector3 pos = new Vector3(x, y, z);
            Entity entity = createAsteroid(engine, pos, mass, radius);

            DebrisComponent debris = entity.getComponent(DebrisComponent.class);

            float vOrbit = (float) Math.sqrt(1e10 / r);
            debris.velocity.set(
                -vOrbit * MathUtils.sin(angle),
                0f,
                vOrbit * MathUtils.cos(angle)
            );

            entities.add(entity);
        }

        return entities;
    }

    public static DebrisClass classifyByRadius(float radius) {
        if (radius < 1f) return DebrisClass.DUST;
        if (radius < 5f) return DebrisClass.PEBBLE;
        if (radius < 50f) return DebrisClass.ROCK;
        if (radius < 200f) return DebrisClass.BOULDER;
        if (radius < 50000f) return DebrisClass.ASTEROID;
        return DebrisClass.PLANETOID;
    }
}
