package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.planet.BiomeType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Loads alien-plant {@link AlienPlantSpecies} + a per-biome {@link BiomePalette}. */
public class AlienPlantRegistry {
    private final Map<String, AlienPlantSpecies> species = new HashMap<>();
    private final Map<BiomeType, BiomePalette> palettes = new EnumMap<>(BiomeType.class);

    public void load(String speciesAndPalettePath) {
        String json = Gdx.files.internal(speciesAndPalettePath).readString();
        loadSpecies(json);
        loadPalette(json);
    }

    public void loadSpecies(String json) {
        JsonValue arr = new JsonReader().parse(json).get("species");
        if (arr == null) return;
        for (JsonValue e = arr.child; e != null; e = e.next) {
            AlienPlantSpecies s = new AlienPlantSpecies();
            s.id = e.getString("id");
            s.archetype = AlienArchetype.valueOf(e.getString("archetype", s.archetype.name()));
            JsonValue st = e.get("stalk");
            if (st != null) {
                float[] h = pair(st.get("height"), s.stalkHeightMin, s.stalkHeightMax);
                s.stalkHeightMin = h[0]; s.stalkHeightMax = h[1];
                s.stalkBaseRadius = st.getFloat("baseRadius", s.stalkBaseRadius);
                s.stalkTaper = st.getFloat("taper", s.stalkTaper);
                s.stalkSides = st.getInt("sides", s.stalkSides);
                s.stalkColor = color(st.getString("color", null), s.stalkColor);
            }
            JsonValue c = e.get("canopy");
            if (c != null) {
                s.canopyColor = color(c.getString("color", null), s.canopyColor);
                s.canopyEmissive = c.getFloat("emissive", s.canopyEmissive);
                int[] clumps = ipair(c.get("clumps"), s.clumpsMin, s.clumpsMax);
                s.clumpsMin = clumps[0]; s.clumpsMax = clumps[1];
                float[] cr = pair(c.get("radius"), s.clumpRadiusMin, s.clumpRadiusMax);
                s.clumpRadiusMin = cr[0]; s.clumpRadiusMax = cr[1];
                float[] mr = pair(c.get("mouthRadius"), s.mouthRadiusMin, s.mouthRadiusMax);
                s.mouthRadiusMin = mr[0]; s.mouthRadiusMax = mr[1];
                float[] dp = pair(c.get("depth"), s.canopyDepthMin, s.canopyDepthMax);
                s.canopyDepthMin = dp[0]; s.canopyDepthMax = dp[1];
                s.lureEmissive = c.getFloat("lureEmissive", s.lureEmissive);
                int[] sh = ipair(c.get("shards"), s.shardsMin, s.shardsMax);
                s.shardsMin = sh[0]; s.shardsMax = sh[1];
                float[] sl = pair(c.get("length"), s.shardLenMin, s.shardLenMax);
                s.shardLenMin = sl[0]; s.shardLenMax = sl[1];
            }
            JsonValue d = e.get("details");
            if (d != null) {
                int[] cnt = ipair(d.get("count"), s.detailCountMin, s.detailCountMax);
                s.detailCountMin = cnt[0]; s.detailCountMax = cnt[1];
                s.detailEmissive = d.getFloat("emissive", s.detailEmissive);
                int[] teeth = ipair(d.get("teeth"), s.teethMin, s.teethMax);
                s.teethMin = teeth[0]; s.teethMax = teeth[1];
                int[] sub = ipair(d.get("subShards"), s.subShardsMin, s.subShardsMax);
                s.subShardsMin = sub[0]; s.subShardsMax = sub[1];
            }
            s.prototypeVariants = e.getInt("prototypeVariants", s.prototypeVariants);
            species.put(s.id, s);
        }
    }

    public void loadPalette(String json) {
        JsonValue arr = new JsonReader().parse(json).get("palette");
        if (arr == null) return;
        for (JsonValue e = arr.child; e != null; e = e.next) {
            BiomeType biome = BiomeType.valueOf(e.getString("biome"));
            BiomePalette p = new BiomePalette(biome);
            p.density = e.getFloat("density", 0f);
            JsonValue sp = e.get("species");
            if (sp != null) for (JsonValue se = sp.child; se != null; se = se.next) {
                p.add(se.getString("id"), se.getFloat("weight", 1f));
            }
            palettes.put(biome, p);
        }
    }

    public AlienPlantSpecies species(String id) { return species.get(id); }
    public java.util.Collection<AlienPlantSpecies> allSpecies() { return species.values(); }
    public BiomePalette palette(BiomeType biome) { return palettes.get(biome); }

    private static float[] pair(JsonValue v, float lo, float hi) {
        if (v == null || !v.isArray() || v.size < 2) return new float[]{lo, hi};
        return new float[]{ v.getFloat(0), v.getFloat(1) };
    }
    private static int[] ipair(JsonValue v, int lo, int hi) {
        if (v == null || !v.isArray() || v.size < 2) return new int[]{lo, hi};
        return new int[]{ v.getInt(0), v.getInt(1) };
    }
    private static Color color(String hex, Color fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        return Color.valueOf(h);
    }
}
