package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.tectonic.Plate;
import com.galacticodyssey.planet.tectonic.TectonicConfig;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainNoiseStackTectonicTest {

    private TectonicModel twoContinentModel() {
        Plate a = new Plate(0, new Vector3(1, 0, 0), false, 0.4f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0), false, 0.4f, new Vector3(0,1,0), -1f);
        return new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
    }

    private BiomeMap biomeMap() {
        return new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.of(BiomeType.GRASSLAND));
    }

    @Test
    void modelMakesGenerationDeterministic() {
        TerrainNoiseStack a = new TerrainNoiseStack(42L, twoContinentModel());
        TerrainNoiseStack b = new TerrainNoiseStack(42L, twoContinentModel());
        Vector3 dir = new Vector3(0.6f, 0.2f, 0.3f).nor();
        assertEquals(a.heightAt(dir, biomeMap(), 3), b.heightAt(dir, biomeMap(), 3), 1e-6f);
    }

    @Test
    void tectonicBaseShiftsHeightVsNoiseOnly() {
        // Deep continental interior should sit higher than the same point with no tectonics.
        Vector3 interior = new Vector3(1, 0, 0).nor();
        TerrainNoiseStack withTec = new TerrainNoiseStack(42L, twoContinentModel());
        TerrainNoiseStack noiseOnly = new TerrainNoiseStack(42L); // legacy path, tectonic == null
        float ht = withTec.heightAt(interior, biomeMap(), 3);
        float hn = noiseOnly.heightAt(interior, biomeMap(), 3);
        assertNotEquals(ht, hn, 1e-4f, "tectonic base must change the continent term");
    }
}
