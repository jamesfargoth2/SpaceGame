package com.galacticodyssey.ship.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipModuleRegistry {

    private final Map<String, ShipModuleData> modules = new HashMap<>();
    private final Map<String, SlotLayout> slotLayouts = new HashMap<>();

    public void loadModules(String path) {
        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ShipModuleData data = new ShipModuleData();
            data.id = entry.getString("id");
            data.name = entry.getString("name");
            data.description = entry.getString("description", "");
            data.category = ShipModuleCategory.valueOf(entry.getString("category"));
            data.size = HardpointSize.valueOf(entry.getString("size"));
            data.powerDraw = entry.getFloat("powerDraw");
            data.mass = entry.getFloat("mass");
            data.qualityTier = QualityTier.valueOf(entry.getString("qualityTier", "COMMON"));
            data.price = entry.getInt("price", 0);

            JsonValue stats = entry.get("stats");
            if (stats != null) {
                for (JsonValue s = stats.child; s != null; s = s.next) {
                    data.stats.put(s.name, s.asFloat());
                }
            }
            modules.put(data.id, data);
        }
    }

    public void loadSlotLayouts(String path) {
        JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
        for (JsonValue ship = root.child; ship != null; ship = ship.next) {
            String shipClassId = ship.name;
            SlotLayout layout = new SlotLayout();
            layout.maxMass = ship.getFloat("maxMass");

            JsonValue pts = ship.get("silhouettePoints");
            if (pts != null) {
                layout.silhouettePoints = new float[pts.size * 2];
                int i = 0;
                for (JsonValue pt = pts.child; pt != null; pt = pt.next) {
                    layout.silhouettePoints[i++] = pt.get(0).asFloat();
                    layout.silhouettePoints[i++] = pt.get(1).asFloat();
                }
            }

            JsonValue wings = ship.get("wingPoints");
            if (wings != null) {
                layout.wingPolygons = new ArrayList<>();
                for (JsonValue wing = wings.child; wing != null; wing = wing.next) {
                    float[] wPts = new float[wing.size * 2];
                    int wi = 0;
                    for (JsonValue wp = wing.child; wp != null; wp = wp.next) {
                        wPts[wi++] = wp.get(0).asFloat();
                        wPts[wi++] = wp.get(1).asFloat();
                    }
                    layout.wingPolygons.add(wPts);
                }
            }

            JsonValue engines = ship.get("engineGlows");
            if (engines != null) {
                layout.engineGlows = new float[engines.size * 2];
                int ei = 0;
                for (JsonValue eg = engines.child; eg != null; eg = eg.next) {
                    layout.engineGlows[ei++] = eg.get(0).asFloat();
                    layout.engineGlows[ei++] = eg.get(1).asFloat();
                }
            }

            JsonValue slotsArr = ship.get("moduleSlots");
            for (JsonValue sl = slotsArr.child; sl != null; sl = sl.next) {
                SlotTemplate t = new SlotTemplate();
                t.id = sl.getString("id");
                t.slotType = ModuleSlotType.valueOf(sl.getString("slotType"));
                t.size = HardpointSize.valueOf(sl.getString("size"));
                t.mandatory = sl.getBoolean("mandatory", false);
                JsonValue pos = sl.get("position");
                t.posX = pos.get(0).asFloat();
                t.posY = pos.get(1).asFloat();
                t.defaultModuleId = sl.getString("defaultModuleId", null);
                layout.slots.add(t);
            }

            slotLayouts.put(shipClassId, layout);
        }
    }

    public ShipModuleData getModule(String id) { return modules.get(id); }

    public ShipModuleData createModuleInstance(String id) {
        ShipModuleData src = modules.get(id);
        return src != null ? new ShipModuleData(src) : null;
    }

    public List<ShipModuleData> getModulesByCategory(ShipModuleCategory category) {
        List<ShipModuleData> result = new ArrayList<>();
        for (ShipModuleData mod : modules.values()) {
            if (mod.category == category) result.add(mod);
        }
        return result;
    }

    public List<ShipModuleData> getModulesForSize(HardpointSize maxSize) {
        List<ShipModuleData> result = new ArrayList<>();
        for (ShipModuleData mod : modules.values()) {
            if (mod.size.ordinal() <= maxSize.ordinal()) result.add(mod);
        }
        return result;
    }

    public void registerModule(ShipModuleData data) { modules.put(data.id, data); }

    public SlotLayout getSlotLayout(String shipClassId) { return slotLayouts.get(shipClassId); }

    public static class SlotLayout {
        public float maxMass;
        public float[] silhouettePoints;
        public List<float[]> wingPolygons;
        public float[] engineGlows;
        public final List<SlotTemplate> slots = new ArrayList<>();
    }

    public static class SlotTemplate {
        public String id;
        public ModuleSlotType slotType;
        public HardpointSize size;
        public boolean mandatory;
        public float posX, posY;
        public String defaultModuleId;
    }
}
