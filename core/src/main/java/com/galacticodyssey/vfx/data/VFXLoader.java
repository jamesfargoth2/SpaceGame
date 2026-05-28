package com.galacticodyssey.vfx.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.vfx.VFXEnums.BlendMode;

public final class VFXLoader {

    private static final String[] EFFECT_IDS = {
        "engine_exhaust", "impact_sparks", "muzzle_flash_ballistic", "shield_ripple",
        "muzzle_flash_energy", "muzzle_flash_plasma", "impact_explosion"
    };

    /** Call once after Gdx.files is available (GL thread). */
    public static void loadAll(VFXRegistry registry, VFXEventBindings bindings) {
        JsonReader reader = new JsonReader();
        for (String id : EFFECT_IDS) {
            String path = "data/vfx/" + id + ".json";
            try {
                JsonValue json = reader.parse(Gdx.files.internal(path));
                registry.register(parseEffect(json));
            } catch (Exception e) {
                Gdx.app.error("VFXLoader", "Skipping missing effect: " + path + " — " + e.getMessage());
            }
        }
        try {
            JsonValue bindingsJson = reader.parse(Gdx.files.internal("data/vfx/vfx_event_bindings.json"));
            parseBindings(bindings, bindingsJson);
        } catch (Exception e) {
            Gdx.app.error("VFXLoader", "Failed to load vfx_event_bindings.json — " + e.getMessage());
        }
    }

    public static ParticleEffectDefinition parseEffect(JsonValue json) {
        ParticleEffectDefinition def = new ParticleEffectDefinition();
        def.id = json.getString("id");
        def.type = json.getString("type", "BILLBOARD");
        def.sprite = json.getString("sprite", "smoke");
        def.mesh = json.getString("mesh", "");
        def.maxParticles = json.getInt("maxParticles", 16);
        def.emitRate = json.getFloat("emitRate", 0f);
        def.burstCount = json.getInt("burstCount", 0);
        def.emitOnce = json.getBoolean("emitOnce", false);
        def.lifetimeMin = json.getFloat("lifetimeMin", 0.5f);
        def.lifetimeMax = json.getFloat("lifetimeMax", 1.0f);
        def.speedMin = json.getFloat("speedMin", 1f);
        def.speedMax = json.getFloat("speedMax", 5f);
        def.spread = json.getFloat("spread", 30f);
        def.sizeMin = json.getFloat("sizeMin", 0.1f);
        def.sizeMax = json.getFloat("sizeMax", 0.3f);
        def.sizeEnd = json.getFloat("sizeEnd", 0f);
        def.color = json.getString("color", "#FFFFFF");
        def.colorEnd = json.getString("colorEnd", "#FFFFFF");
        def.texture = json.getString("texture", "particles/default.png");
        def.blendMode = BlendMode.valueOf(json.getString("blendMode", "ADDITIVE"));
        def.gravity = json.getFloat("gravity", 0f);
        def.bounce = json.getFloat("bounce", 0.3f);
        def.duration = json.getFloat("duration", -1f);
        return def;
    }

    public static void parseBindings(VFXEventBindings bindings, JsonValue json) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (JsonValue entry = json.child; entry != null; entry = entry.next) {
            map.put(entry.name, entry.asString());
        }
        bindings.loadFromMap(map);
    }

    private VFXLoader() {}
}
