package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

public class DebrisComponent implements Component, Pool.Poolable {

    public enum DebrisClass { DUST, PEBBLE, ROCK, BOULDER, ASTEROID, PLANETOID }
    public enum SimLevel { FULL, ORBITAL, STATIC }

    public DebrisClass debrisClass = DebrisClass.ROCK;
    public SimLevel simulationLevel = SimLevel.FULL;
    public final Vector3 velocity = new Vector3();
    public final Vector3 angularVelocity = new Vector3();
    public final Vector3 inertiaTensor = new Vector3(1, 1, 1);
    public float mass = 100f;
    public float radius = 5f;
    public float fractureEnergy = 1000f;
    public float distanceToPlayer;

    @Override
    public void reset() {
        debrisClass = DebrisClass.ROCK;
        simulationLevel = SimLevel.FULL;
        velocity.setZero();
        angularVelocity.setZero();
        inertiaTensor.set(1, 1, 1);
        mass = 100f;
        radius = 5f;
        fractureEnergy = 1000f;
        distanceToPlayer = 0f;
    }
}
