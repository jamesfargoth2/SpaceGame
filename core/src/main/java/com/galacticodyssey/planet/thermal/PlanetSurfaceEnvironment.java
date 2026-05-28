package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Gas;

/**
 * {@link ThermalEnvironment} backed by the active planet's biome/climate/atmosphere data.
 * The active surface scene is a local patch centered on (sceneLat, sceneLon); local-scene
 * metres are converted to small lat/lon offsets via the planet radius so biome variation
 * across the patch is preserved.
 */
public class PlanetSurfaceEnvironment implements ThermalEnvironment {
    private final BiomeMap biomeMap;
    private final Atmosphere atmosphere;
    private final float sceneLatRad;
    private final float sceneLonRad;
    private final float planetRadius;
    private final float cosLat;

    public PlanetSurfaceEnvironment(BiomeMap biomeMap, Atmosphere atmosphere,
                                    float sceneLatRad, float sceneLonRad, float planetRadius) {
        this.biomeMap = biomeMap;
        this.atmosphere = atmosphere;
        this.sceneLatRad = sceneLatRad;
        this.sceneLonRad = sceneLonRad;
        this.planetRadius = Math.max(1f, planetRadius);
        this.cosLat = Math.max(0.01f, (float) Math.cos(sceneLatRad));
    }

    private float latAt(Vector3 p) { return sceneLatRad + p.z / planetRadius; }
    private float lonAt(Vector3 p) { return sceneLonRad + p.x / (planetRadius * cosLat); }

    @Override
    public float ambientTemp(Vector3 localPos) {
        return biomeMap.getTemperature(latAt(localPos), lonAt(localPos));
    }

    @Override
    public float oxygenFraction(Vector3 localPos) {
        if (atmosphere == null) return 0f;
        Float o2 = atmosphere.composition.get(Gas.O2);
        return o2 == null ? 0f : o2;
    }

    @Override
    public void wind(Vector3 localPos, Vector3 out) {
        // Without ClimateData wind sampling here we expose a calm baseline; WildfireSystem
        // tolerates zero wind (no directional bias). Climate-driven wind can be layered in
        // by sampling ClimateData.windU/windV at latAt/lonAt when a ClimateData ref is wired.
        out.set(0f, 0f, 0f);
    }
}
