package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.shipbuilder.ModuleCatalogEntry;

import java.util.*;

public class HardpointMarkerRenderer implements Disposable {
    public static class HardpointDef {
        public final String id;
        public final Vector3 position;
        public final ModuleCatalogEntry.HardpointType type;
        public final ModuleCatalogEntry.HardpointSize size;
        public HardpointDef(String id, Vector3 position, ModuleCatalogEntry.HardpointType type,
                           ModuleCatalogEntry.HardpointSize size) {
            this.id = id;
            this.position = position;
            this.type = type;
            this.size = size;
        }
    }

    private final List<HardpointDef> hardpoints = new ArrayList<>();
    private final Array<ModelInstance> markers = new Array<>();
    private Model weaponMarker, engineMarker, utilityMarker;
    private int selectedIndex = -1;

    public void build() {
        ModelBuilder mb = new ModelBuilder();
        float s = 0.4f;
        weaponMarker = mb.createBox(s, s, s,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        engineMarker = mb.createSphere(s, s, s, 8, 6,
            new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        utilityMarker = mb.createSphere(s, s, s, 6, 6,
            new Material(ColorAttribute.createDiffuse(Color.CYAN)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void generateFromHull(HullGeometry hull) {
        hardpoints.clear();
        markers.clear();
        if (hull.hardpoints == null) return;
        for (int i = 0; i < hull.hardpoints.length; i++) {
            Vector3 pos = hull.hardpoints[i];
            ModuleCatalogEntry.HardpointType type = ModuleCatalogEntry.HardpointType.WEAPON;
            String prefix = "WPN";
            if (pos.z > hull.boundingBox.max.z * 0.7f) {
                type = ModuleCatalogEntry.HardpointType.ENGINE;
                prefix = "ENG";
            } else if (Math.abs(pos.y) > hull.boundingBox.max.y * 0.5f) {
                type = ModuleCatalogEntry.HardpointType.UTILITY;
                prefix = "UTL";
            }
            String id = prefix + "-" + (i + 1);
            hardpoints.add(new HardpointDef(id, pos, type, ModuleCatalogEntry.HardpointSize.M));

            Model m = type == ModuleCatalogEntry.HardpointType.WEAPON ? weaponMarker
                    : type == ModuleCatalogEntry.HardpointType.ENGINE ? engineMarker
                    : utilityMarker;
            ModelInstance inst = new ModelInstance(m);
            inst.transform.setToTranslation(pos);
            markers.add(inst);
        }
    }

    public int pick(Ray ray) {
        float closest = Float.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < hardpoints.size(); i++) {
            Vector3 p = hardpoints.get(i).position;
            Vector3 projected = new Vector3();
            float t = ray.direction.dot(new Vector3(p).sub(ray.origin));
            projected.set(ray.direction).scl(t).add(ray.origin);
            float dist = projected.dst(p);
            float camDist = ray.origin.dst(p);
            if (dist < 0.6f && camDist < closest) {
                closest = camDist;
                idx = i;
            }
        }
        selectedIndex = idx;
        return idx;
    }

    public HardpointDef getHardpoint(int index) {
        return hardpoints.get(index);
    }

    public List<HardpointDef> getHardpoints() { return hardpoints; }
    public int getSelectedIndex() { return selectedIndex; }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : markers) batch.render(inst, env);
    }

    @Override
    public void dispose() {
        if (weaponMarker != null) weaponMarker.dispose();
        if (engineMarker != null) engineMarker.dispose();
        if (utilityMarker != null) utilityMarker.dispose();
    }
}
