package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.BiomeType;

/** Loads the grass {@link GrassConfig} from JSON (data/flora/grass.json). */
public class GrassRegistry {
    private final GrassConfig config = new GrassConfig();

    public void load(String path) { loadFromJson(Gdx.files.internal(path).readString()); }

    public void loadFromJson(String json) {
        JsonValue root = new JsonReader().parse(json);
        config.cellSize = root.getFloat("cellSize", config.cellSize);
        config.radius = root.getFloat("radius", config.radius);
        config.fadeBand = root.getFloat("fadeBand", config.fadeBand);
        config.baseTuftsPerM2 = root.getFloat("baseTuftsPerM2", config.baseTuftsPerM2);
        config.bladesPerTuft = root.getInt("bladesPerTuft", config.bladesPerTuft);
        config.maxCachedCells = root.getInt("maxCachedCells", config.maxCachedCells);
        JsonValue wind = root.get("wind");
        if (wind != null) {
            config.windAmplitude = wind.getFloat("amplitude", config.windAmplitude);
            config.windFrequency = wind.getFloat("frequency", config.windFrequency);
        }
        JsonValue biomes = root.get("biomes");
        if (biomes != null) {
            for (JsonValue e = biomes.child; e != null; e = e.next) {
                BiomeType biome = BiomeType.valueOf(e.getString("biome"));
                GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
                g.density = e.getFloat("density", 0f);
                JsonValue h = e.get("height");
                g.heightMin = (h != null && h.size >= 2) ? h.getFloat(0) : 0.3f;
                g.heightMax = (h != null && h.size >= 2) ? h.getFloat(1) : 0.7f;
                Color a = color(e.getString("colorA", "3a6b22"));
                Color b = color(e.getString("colorB", "5a8a2e"));
                g.colorAr = a.r; g.colorAg = a.g; g.colorAb = a.b;
                g.colorBr = b.r; g.colorBg = b.g; g.colorBb = b.b;
                config.put(biome, g);
            }
        }
    }

    public GrassConfig config() { return config; }

    private static Color color(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        return Color.valueOf(h);
    }
}
