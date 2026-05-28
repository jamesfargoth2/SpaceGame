package com.galacticodyssey.ship.power;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

public class ReactorSpecRegistry {

    private final Map<String, ReactorSpec> byId = new HashMap<>();
    private final Map<String, ReactorSpec> bySizeClass = new HashMap<>();

    public void loadFromFile(String path) {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ReactorSpec spec = new ReactorSpec();
            spec.id = entry.getString("id");
            spec.name = entry.getString("name", spec.id);
            spec.sizeClass = entry.getString("sizeClass", "SMALL");
            spec.baseOutput = entry.getFloat("baseOutput", 100f);
            spec.wasteHeatFactor = entry.getFloat("wasteHeatFactor", 0.15f);
            spec.batteryCapacity = entry.getFloat("batteryCapacity", 500f);
            spec.batteryChargeRate = entry.getFloat("batteryChargeRate", 50f);
            spec.batteryDischargeRate = entry.getFloat("batteryDischargeRate", 80f);
            spec.capacitorCapacity = entry.getFloat("capacitorCapacity", 100f);
            spec.capacitorChargeRate = entry.getFloat("capacitorChargeRate", 80f);
            spec.engineMaxDraw = entry.getFloat("engineMaxDraw", 50f);
            spec.weaponMaxDraw = entry.getFloat("weaponMaxDraw", 20f);
            spec.shieldMaxDraw = entry.getFloat("shieldMaxDraw", 15f);
            spec.lifeSupportDraw = entry.getFloat("lifeSupportDraw", 5f);
            spec.sensorMaxDraw = entry.getFloat("sensorMaxDraw", 10f);
            byId.put(spec.id, spec);
            bySizeClass.put(spec.sizeClass, spec);
        }
    }

    public ReactorSpec getById(String id) {
        return byId.get(id);
    }

    public ReactorSpec getBySizeClass(String sizeClass) {
        return bySizeClass.get(sizeClass);
    }
}
