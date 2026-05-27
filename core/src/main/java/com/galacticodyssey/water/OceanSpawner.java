package com.galacticodyssey.water;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import java.util.Random;

public class OceanSpawner {

    private final Engine engine;
    private Entity oceanEntity;

    public OceanSpawner(Engine engine) {
        this.engine = engine;
    }

    public Entity spawnOcean(float baseHeight, float density, float viscosity,
                             long planetSeed, int waveCount) {
        if (oceanEntity != null) {
            despawnOcean();
        }

        oceanEntity = new Entity();
        WaterBodyComponent water = new WaterBodyComponent();
        water.baseHeight = baseHeight;
        water.density = density;
        water.kinematicViscosity = viscosity;

        Random rng = new Random(planetSeed);
        for (int i = 0; i < waveCount; i++) {
            WaveParams wave = new WaveParams();
            wave.amplitude = 0.3f + rng.nextFloat() * 1.5f;
            wave.wavelength = 20f + rng.nextFloat() * 80f;
            float g = 9.81f;
            wave.speed = (float) Math.sqrt(g * wave.wavelength / MathUtils.PI2);
            wave.steepness = 0.1f + rng.nextFloat() * 0.4f;
            wave.directionDeg = rng.nextFloat() * 360f;
            water.waves.add(wave);
        }

        oceanEntity.add(water);
        engine.addEntity(oceanEntity);
        return oceanEntity;
    }

    public Entity spawnSeawater(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 1025f, 1.19e-6f, planetSeed, 5);
    }

    public Entity spawnMethane(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 450f, 2.2e-7f, planetSeed, 3);
    }

    public Entity spawnLava(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 2700f, 1e3f, planetSeed, 2);
    }

    public Entity spawnAmmonia(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 680f, 3.5e-7f, planetSeed, 4);
    }

    public Entity spawnBrine(float baseHeight, long planetSeed) {
        return spawnOcean(baseHeight, 1200f, 1.5e-6f, planetSeed, 5);
    }

    public void despawnOcean() {
        if (oceanEntity != null) {
            engine.removeEntity(oceanEntity);
            oceanEntity = null;
        }
    }

    public boolean hasOcean() {
        return oceanEntity != null;
    }
}
