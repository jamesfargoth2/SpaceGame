package com.galacticodyssey.water.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

public class WaterDataRegistry {

    private SwimConfigData swimConfig;
    private DepthZonesConfig depthZonesConfig;
    private final Map<String, DiveGearDefinition> diveGear = new HashMap<>();
    private final Map<String, WeatherProfileData> weatherProfiles = new HashMap<>();
    private StormConfigData stormConfig;

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();

        swimConfig = json.fromJson(SwimConfigData.class,
            Gdx.files.internal("data/water/swim_config.json"));

        depthZonesConfig = json.fromJson(DepthZonesConfig.class,
            Gdx.files.internal("data/water/depth_zones.json"));

        JsonValue gearRoot = reader.parse(Gdx.files.internal("data/water/dive_gear.json"));
        for (JsonValue entry = gearRoot.child; entry != null; entry = entry.next) {
            DiveGearDefinition def = json.readValue(DiveGearDefinition.class, entry);
            diveGear.put(def.id, def);
        }

        JsonValue profilesRoot = reader.parse(
            Gdx.files.internal("data/water/weather_profiles.json"));
        for (JsonValue entry = profilesRoot.child; entry != null; entry = entry.next) {
            WeatherProfileData profile = json.readValue(WeatherProfileData.class, entry);
            weatherProfiles.put(profile.phase, profile);
        }

        stormConfig = json.fromJson(StormConfigData.class,
            Gdx.files.internal("data/water/storm_config.json"));
    }

    public SwimConfigData getSwimConfig() { return swimConfig; }
    public DepthZonesConfig getDepthZonesConfig() { return depthZonesConfig; }
    public DiveGearDefinition getDiveGear(String id) { return diveGear.get(id); }
    public WeatherProfileData getWeatherProfile(String phase) { return weatherProfiles.get(phase); }
    public StormConfigData getStormConfig() { return stormConfig; }

    public void setSwimConfig(SwimConfigData config) { this.swimConfig = config; }
    public void setDepthZonesConfig(DepthZonesConfig config) { this.depthZonesConfig = config; }
    public void setStormConfig(StormConfigData config) { this.stormConfig = config; }
    public void registerDiveGear(DiveGearDefinition def) { diveGear.put(def.id, def); }
    public void registerWeatherProfile(WeatherProfileData profile) {
        weatherProfiles.put(profile.phase, profile);
    }
}
