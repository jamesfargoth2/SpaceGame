package com.galacticodyssey.ship.weapons.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipWeaponRegistry {
    private final Map<String, ShipWeaponData> weapons = new HashMap<>();
    private final Map<String, List<HardpointTemplate>> hardpointTemplates = new HashMap<>();

    public void loadWeapons(String path) {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ShipWeaponData data = new ShipWeaponData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.category = ShipWeaponCategory.valueOf(entry.getString("category"));
            data.damage = entry.getFloat("damage");
            data.damageType = DamageType.valueOf(entry.getString("damageType"));
            data.fireRate = entry.getFloat("fireRate");
            data.projectileSpeed = entry.getFloat("projectileSpeed");
            data.range = entry.getFloat("range");
            data.energyCost = entry.getFloat("energyCost");
            data.heatPerShot = entry.getFloat("heatPerShot");
            if (entry.has("ammoCapacity")) {
                data.ammoCapacity = entry.getInt("ammoCapacity");
                data.currentAmmo = data.ammoCapacity;
            }
            data.trackingSpeed = entry.getFloat("trackingSpeed", 0f);
            data.burstCount = entry.getInt("burstCount", 1);
            data.burstDelay = entry.getFloat("burstDelay", 0f);
            weapons.put(data.id, data);
        }
    }

    public void loadHardpointTemplates(String path) {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue ship = root.child; ship != null; ship = ship.next) {
            String shipId = ship.getString("shipClass");
            List<HardpointTemplate> templates = new ArrayList<>();
            for (JsonValue hp = ship.get("hardpoints").child; hp != null; hp = hp.next) {
                HardpointTemplate t = new HardpointTemplate();
                t.id = hp.getString("id");
                t.posX = hp.getFloat("posX");
                t.posY = hp.getFloat("posY");
                t.posZ = hp.getFloat("posZ");
                t.type = HardpointType.valueOf(hp.getString("type"));
                t.sizeClass = HardpointSize.valueOf(hp.getString("sizeClass"));
                t.arcMin = hp.getFloat("arcMin", 0f);
                t.arcMax = hp.getFloat("arcMax", 360f);
                t.defaultWeaponId = hp.getString("defaultWeaponId", null);
                templates.add(t);
            }
            hardpointTemplates.put(shipId, templates);
        }
    }

    public ShipWeaponData getWeapon(String id) { return weapons.get(id); }

    public ShipWeaponData createWeaponInstance(String id) {
        ShipWeaponData template = weapons.get(id);
        if (template == null) return null;
        ShipWeaponData instance = new ShipWeaponData();
        instance.id = template.id;
        instance.name = template.name;
        instance.category = template.category;
        instance.damage = template.damage;
        instance.damageType = template.damageType;
        instance.fireRate = template.fireRate;
        instance.projectileSpeed = template.projectileSpeed;
        instance.range = template.range;
        instance.energyCost = template.energyCost;
        instance.heatPerShot = template.heatPerShot;
        instance.ammoCapacity = template.ammoCapacity;
        instance.currentAmmo = template.ammoCapacity;
        instance.trackingSpeed = template.trackingSpeed;
        instance.burstCount = template.burstCount;
        instance.burstDelay = template.burstDelay;
        return instance;
    }

    public List<HardpointTemplate> getHardpointTemplates(String shipClass) {
        return hardpointTemplates.getOrDefault(shipClass, List.of());
    }

    public void registerWeapon(ShipWeaponData data) { weapons.put(data.id, data); }
}
