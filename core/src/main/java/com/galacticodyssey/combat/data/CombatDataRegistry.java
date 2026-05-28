package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.HashMap;
import java.util.Map;

public class CombatDataRegistry {
    private final Map<StatusEffectType, StatusEffectData> statusEffects = new HashMap<>();
    private final Map<String, AIArchetypeData> aiArchetypes = new HashMap<>();
    private DamageConfigData damageConfig;

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

        JsonValue effectsRoot = reader.parse(Gdx.files.internal("data/combat/status_effects.json"));
        for (JsonValue entry = effectsRoot.child; entry != null; entry = entry.next) {
            StatusEffectData data = json.readValue(StatusEffectData.class, entry);
            statusEffects.put(data.type, data);
        }

        JsonValue archetypesRoot = reader.parse(Gdx.files.internal("data/combat/ai_archetypes.json"));
        for (JsonValue entry = archetypesRoot.child; entry != null; entry = entry.next) {
            AIArchetypeData data = json.readValue(AIArchetypeData.class, entry);
            aiArchetypes.put(data.id, data);
        }

        damageConfig = json.fromJson(DamageConfigData.class, Gdx.files.internal("data/combat/damage_config.json"));
    }

    public StatusEffectData getStatusEffect(StatusEffectType type) { return statusEffects.get(type); }
    public AIArchetypeData getArchetype(String id) { return aiArchetypes.get(id); }
    public DamageConfigData getDamageConfig() { return damageConfig; }

    public void registerStatusEffect(StatusEffectData data) { statusEffects.put(data.type, data); }
    public void registerArchetype(AIArchetypeData data) { aiArchetypes.put(data.id, data); }
    public void setDamageConfig(DamageConfigData config) { this.damageConfig = config; }
}
