package com.galacticodyssey.city.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Loads and serves city layout content (size tiers, district mix, faction form bias). */
public class CityDataRegistry {

    private final List<SizeTierDef> sizeTiers = new ArrayList<>();
    private final Map<DistrictType, DistrictMixDef> districtMix = new EnumMap<>(DistrictType.class);
    private final Map<FactionEthos, List<CityForm>> factionFormBias = new EnumMap<>(FactionEthos.class);

    /** Runtime load via the libGDX files backend. */
    public void loadFromFiles() {
        loadSizeTiers(Gdx.files.internal("data/cities/size_tiers.json").reader());
        loadDistrictMix(Gdx.files.internal("data/cities/district_mix.json").reader());
        loadFactionFormBias(Gdx.files.internal("data/cities/faction_form_bias.json").reader());
    }

    /** Test/standalone load straight off the JVM classpath - no Gdx backend required. */
    public void loadFromClasspath() {
        loadSizeTiers(classpathReader("data/cities/size_tiers.json"));
        loadDistrictMix(classpathReader("data/cities/district_mix.json"));
        loadFactionFormBias(classpathReader("data/cities/faction_form_bias.json"));
    }

    private Reader classpathReader(String path) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("Missing classpath resource: " + path);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    void loadSizeTiers(Reader reader) {
        Json json = new Json();
        JsonValue root = new JsonReader().parse(reader);
        sizeTiers.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            sizeTiers.add(json.readValue(SizeTierDef.class, e));
        }
    }

    void loadDistrictMix(Reader reader) {
        Json json = new Json();
        JsonValue root = new JsonReader().parse(reader);
        districtMix.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            DistrictType type = DistrictType.valueOf(e.name);
            districtMix.put(type, json.readValue(DistrictMixDef.class, e));
        }
    }

    void loadFactionFormBias(Reader reader) {
        JsonValue root = new JsonReader().parse(reader);
        factionFormBias.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            FactionEthos ethos = FactionEthos.valueOf(e.name);
            List<CityForm> forms = new ArrayList<>();
            for (JsonValue f = e.child; f != null; f = f.next) {
                forms.add(CityForm.valueOf(f.asString()));
            }
            factionFormBias.put(ethos, forms);
        }
    }

    public List<SizeTierDef> sizeTiers() { return sizeTiers; }

    public SizeTierDef tierForPopulation(int population) {
        for (SizeTierDef t : sizeTiers) {
            if (population >= t.minPopulation && population <= t.maxPopulation) return t;
        }
        // Fall back to the last (largest) tier for out-of-range high values.
        return sizeTiers.get(sizeTiers.size() - 1);
    }

    public DistrictMixDef districtMix(DistrictType type) {
        return districtMix.getOrDefault(type, districtMix.get(DistrictType.UNKNOWN));
    }

    public List<CityForm> factionFormBias(FactionEthos ethos) {
        return factionFormBias.getOrDefault(ethos, new ArrayList<>());
    }
}
