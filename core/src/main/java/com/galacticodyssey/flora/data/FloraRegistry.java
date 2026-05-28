package com.galacticodyssey.flora.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.planet.BiomeType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Loads and holds flora {@link FloraSpecies} and per-biome {@link BiomePalette}s. */
public class FloraRegistry {
    private final Map<String, FloraSpecies> species = new HashMap<>();
    private final Map<BiomeType, BiomePalette> palettes = new EnumMap<>(BiomeType.class);

    /** Loads both files from the internal (classpath) filesystem at bootstrap. */
    public void load(String speciesPath, String palettePath) {
        loadSpecies(Gdx.files.internal(speciesPath).readString());
        loadPalettes(Gdx.files.internal(palettePath).readString());
    }

    /** Parses species from a raw JSON string (unit-test friendly). */
    public void loadSpecies(String json) {
        JsonValue arr = new JsonReader().parse(json).get("species");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            FloraSpecies s = new FloraSpecies();
            s.id = e.getString("id");
            s.displayName = e.getString("displayName", s.id);
            s.shape = EnvelopeShape.valueOf(e.getString("shape", s.shape.name()));
            float[] h = floatPair(e.get("height"), s.heightMin, s.heightMax);
            s.heightMin = h[0]; s.heightMax = h[1];
            float[] r = floatPair(e.get("radius"), s.radiusMin, s.radiusMax);
            s.radiusMin = r[0]; s.radiusMax = r[1];

            JsonValue g = e.get("growth");
            if (g != null) {
                s.attractionPoints = g.getInt("attractionPoints", s.attractionPoints);
                s.influenceRadius = g.getFloat("influenceRadius", s.influenceRadius);
                s.killDistance = g.getFloat("killDistance", s.killDistance);
                s.segmentLength = g.getFloat("segmentLength", s.segmentLength);
                s.maxNodes = g.getInt("maxNodes", s.maxNodes);
            }
            JsonValue tr = e.get("trunk");
            if (tr != null) {
                s.trunkSides = tr.getInt("sides", s.trunkSides);
                s.baseRadius = tr.getFloat("baseRadius", s.baseRadius);
                s.taper = tr.getFloat("taper", s.taper);
                s.trunkColor = color(tr.getString("color", null), s.trunkColor);
            }
            JsonValue f = e.get("foliage");
            if (f != null) {
                s.foliageStyle = FoliageStyle.valueOf(f.getString("style", s.foliageStyle.name()));
                s.clumpsPerTip = f.getInt("clumpsPerTip", s.clumpsPerTip);
                float[] cr = floatPair(f.get("clumpRadius"), s.clumpRadiusMin, s.clumpRadiusMax);
                s.clumpRadiusMin = cr[0]; s.clumpRadiusMax = cr[1];
                s.foliageColorA = color(f.getString("colorA", null), s.foliageColorA);
                s.foliageColorB = color(f.getString("colorB", null), s.foliageColorB);
            }
            s.prototypeVariants = e.getInt("prototypeVariants", s.prototypeVariants);
            species.put(s.id, s);
        }
    }

    public FloraSpecies species(String id) { return species.get(id); }
    public java.util.Collection<FloraSpecies> allSpecies() { return species.values(); }
    public BiomePalette palette(BiomeType biome) { return palettes.get(biome); }

    // --- helpers ---

    private static float[] floatPair(JsonValue v, float defLo, float defHi) {
        if (v == null || !v.isArray() || v.size < 2) return new float[]{defLo, defHi};
        return new float[]{ v.getFloat(0), v.getFloat(1) };
    }

    private static Color color(String hex, Color fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff"; // Color.valueOf wants rrggbbaa
        return Color.valueOf(h);
    }

    // loadPalettes() added in Task 4
    private void loadPalettes(String json) { /* stub — implemented in Task 4 */ }
}
