package com.galacticodyssey.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.data.VehicleDefinition.VehicleWeaponStats;

import java.util.HashMap;
import java.util.Map;

/** Loads and holds all {@link VehicleDefinition}s, looked up by id. */
public class VehicleRegistry {
    private final Map<String, VehicleDefinition> definitions = new HashMap<>();

    /** Loads from a classpath/internal file (used at game bootstrap). */
    public void load(String path) {
        loadFromJson(Gdx.files.internal(path).readString());
    }

    /** Parses definitions from a raw JSON string (unit-test friendly). */
    public void loadFromJson(String json) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue arr = root.get("vehicles");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            VehicleDefinition def = new VehicleDefinition();
            def.id = e.getString("id");
            def.displayName = e.getString("displayName", def.id);
            def.modelPath = e.getString("modelPath", null);
            def.sizeClass = e.getString("sizeClass", "LIGHT");
            def.mass = e.getFloat("mass", def.mass);
            def.wheelbase = e.getFloat("wheelbase", def.wheelbase);
            def.trackWidth = e.getFloat("trackWidth", def.trackWidth);
            def.groundClearance = e.getFloat("groundClearance", def.groundClearance);
            def.maxDriveForce = e.getFloat("maxDriveForce", def.maxDriveForce);
            def.maxSteerAngle = e.getFloat("maxSteerAngle", def.maxSteerAngle);
            def.anchorBreakForce = e.getFloat("anchorBreakForce", def.anchorBreakForce);
            def.dynamicLift = e.getFloat("dynamicLift", def.dynamicLift);
            def.maxHP = e.getFloat("maxHP", def.maxHP);
            def.armorValue = e.getFloat("armorValue", def.armorValue);
            def.baySlots = e.getInt("baySlots", def.baySlots);

            JsonValue w = e.get("weapon");
            if (w != null) {
                VehicleWeaponStats ws = new VehicleWeaponStats();
                ws.damage = w.getFloat("damage", ws.damage);
                ws.fireRate = w.getFloat("fireRate", ws.fireRate);
                ws.range = w.getFloat("range", ws.range);
                ws.hitscan = w.getBoolean("hitscan", ws.hitscan);
                ws.projectileSpeed = w.getFloat("projectileSpeed", ws.projectileSpeed);
                ws.damageType = DamageType.valueOf(w.getString("damageType", ws.damageType.name()));
                ws.firingMode = FiringMode.valueOf(w.getString("firingMode", ws.firingMode.name()));
                ws.magSize = w.getInt("magSize", ws.magSize);
                ws.reloadTime = w.getFloat("reloadTime", ws.reloadTime);
                ws.spread = w.getFloat("spread", ws.spread);
                def.weapon = ws;
            }
            definitions.put(def.id, def);
        }
    }

    public VehicleDefinition get(String id) { return definitions.get(id); }

    public java.util.Collection<VehicleDefinition> all() { return definitions.values(); }
}
