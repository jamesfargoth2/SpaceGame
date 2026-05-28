package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class WeaponDataRegistry {
    private final Map<String, WeaponFrameData> frames = new HashMap<>();
    private final Map<String, MeleeFrameData> meleeFrames = new HashMap<>();
    private final Map<String, BarrelData> barrels = new HashMap<>();
    private final Map<String, AmmoTypeData> ammoTypes = new HashMap<>();
    private final Map<String, WeaponModData> mods = new HashMap<>();
    private final Map<String, QualityTierData> qualities = new HashMap<>();

    @SuppressWarnings("unchecked")
    public void loadFromFiles() {
        Json json = new Json() {
            @Override
            protected Object newInstance(Class type) {
                if (type == java.util.Map.class) return new HashMap<>();
                return super.newInstance(type);
            }
        };
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();

        loadArray(json, reader, "data/weapons/frames.json", WeaponFrameData.class, frames, d -> d.id);
        loadArray(json, reader, "data/weapons/melee_frames.json", MeleeFrameData.class, meleeFrames, d -> d.id);
        loadArray(json, reader, "data/weapons/barrels.json", BarrelData.class, barrels, d -> d.id);
        loadArray(json, reader, "data/weapons/ammo_types.json", AmmoTypeData.class, ammoTypes, d -> d.id);
        loadArray(json, reader, "data/weapons/mods.json", WeaponModData.class, mods, d -> d.id);
        loadArray(json, reader, "data/weapons/qualities.json", QualityTierData.class, qualities, d -> d.tier.name());
    }

    private <T> void loadArray(Json json, JsonReader reader, String path, Class<T> type,
                               Map<String, T> target, java.util.function.Function<T, String> idExtractor) {
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            T data = json.readValue(type, entry);
            target.put(idExtractor.apply(data), data);
        }
    }

    public WeaponFrameData getFrame(String id) { return frames.get(id); }
    public MeleeFrameData getMeleeFrame(String id) { return meleeFrames.get(id); }
    public BarrelData getBarrel(String id) { return barrels.get(id); }
    public AmmoTypeData getAmmoType(String id) { return ammoTypes.get(id); }
    public WeaponModData getMod(String id) { return mods.get(id); }
    public QualityTierData getQuality(String tierName) { return qualities.get(tierName); }

    public void registerFrame(WeaponFrameData data) { frames.put(data.id, data); }
    public void registerMeleeFrame(MeleeFrameData data) { meleeFrames.put(data.id, data); }
    public void registerBarrel(BarrelData data) { barrels.put(data.id, data); }
    public void registerAmmoType(AmmoTypeData data) { ammoTypes.put(data.id, data); }
    public void registerMod(WeaponModData data) { mods.put(data.id, data); }
    public void registerQuality(QualityTierData data) { qualities.put(data.tier.name(), data); }
}
