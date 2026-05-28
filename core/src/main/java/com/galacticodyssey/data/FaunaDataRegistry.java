package com.galacticodyssey.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.fauna.archetype.AttachmentNode;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import com.galacticodyssey.fauna.part.Socket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates creature part + archetype definitions from JSON. */
public class FaunaDataRegistry {
    private final Map<String, CreaturePartDef> parts = new HashMap<>();
    private final Map<String, BodyPlanArchetypeDef> archetypes = new HashMap<>();

    public void loadParts(String path)      { loadPartsFromJson(Gdx.files.internal(path).readString()); }
    public void loadArchetypes(String path) { loadArchetypesFromJson(Gdx.files.internal(path).readString()); }

    public void loadPartsFromJson(String json) {
        JsonValue arr = new JsonReader().parse(json).get("parts");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            CreaturePartDef p = new CreaturePartDef();
            p.id = e.getString("id");
            p.partType = PartType.valueOf(e.getString("partType"));
            p.minScale = e.getFloat("minScale", 1f);
            p.maxScale = e.getFloat("maxScale", 1f);
            JsonValue plans = e.get("bodyPlans");
            if (plans != null) for (JsonValue pl = plans.child; pl != null; pl = pl.next)
                p.bodyPlans.add(BodyPlan.valueOf(pl.asString()));
            p.geometry = parseGeometry(e.get("geometry"));
            JsonValue socks = e.get("sockets");
            if (socks != null) for (JsonValue s = socks.child; s != null; s = s.next) {
                Socket sock = new Socket();
                sock.id = s.getString("id");
                sock.acceptedType = PartType.valueOf(s.getString("acceptedType"));
                float[] pos = s.get("pos") != null ? s.get("pos").asFloatArray() : new float[]{0, 0, 0};
                sock.localPosition.set(pos[0], pos[1], pos[2]);
                float[] rot = s.get("rot") != null ? s.get("rot").asFloatArray() : null;
                if (rot != null) sock.localRotation.set(rot[0], rot[1], rot[2], rot[3]);
                sock.mirrorGroup = s.getString("mirrorGroup", null);
                sock.jointHint = s.getString("jointHint", null);
                p.sockets.add(sock);
            }
            parts.put(p.id, p);
        }
    }

    private PartGeometrySpec parseGeometry(JsonValue g) {
        PartGeometrySpec spec = new PartGeometrySpec();
        if (g == null) return spec;
        spec.kind = PartGeometrySpec.Kind.valueOf(g.getString("kind", "PROCEDURAL"));
        if (spec.kind == PartGeometrySpec.Kind.AUTHORED) {
            spec.modelRef = g.getString("modelRef", null);
        } else {
            spec.shape = PartGeometrySpec.Shape.valueOf(g.getString("shape", "CAPSULE"));
            spec.length = g.getFloat("length", 1f);
            spec.radius = g.getFloat("radius", 0.25f);
            spec.taper = g.getFloat("taper", 1f);
        }
        return spec;
    }

    public void loadArchetypesFromJson(String json) {
        JsonValue arr = new JsonReader().parse(json).get("archetypes");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            BodyPlanArchetypeDef a = new BodyPlanArchetypeDef();
            a.id = e.getString("id");
            a.bodyPlan = BodyPlan.valueOf(e.getString("bodyPlan"));
            a.minSize = e.getFloat("minSize", 1f);
            a.maxSize = e.getFloat("maxSize", 1f);
            a.density = e.getFloat("density", 1000f);
            a.gaitClass = e.getString("gaitClass", "walk");
            a.kHp = e.getFloat("kHp", 12f);
            a.kSpeed = e.getFloat("kSpeed", 9f);
            a.kDamage = e.getFloat("kDamage", 4f);
            a.root = parseAttachment(e.get("root"));
            archetypes.put(a.id, a);
        }
    }

    private AttachmentNode parseAttachment(JsonValue n) {
        AttachmentNode node = new AttachmentNode();
        node.socketId = n.getString("socketId", null);
        node.partType = PartType.valueOf(n.getString("partType"));
        node.mirror = n.getBoolean("mirror", false);
        node.repeat = n.getInt("repeat", 1);
        node.continuationSocketId = n.getString("continuationSocketId", null);
        JsonValue kids = n.get("children");
        if (kids != null) for (JsonValue c = kids.child; c != null; c = c.next)
            node.children.add(parseAttachment(c));
        return node;
    }

    /** Throws IllegalStateException if any archetype references a part type with no eligible part. */
    public void validate() {
        Set<PartType> available = new HashSet<>();
        for (CreaturePartDef p : parts.values()) available.add(p.partType);
        for (BodyPlanArchetypeDef a : archetypes.values()) checkNode(a, a.root, available);
    }

    private void checkNode(BodyPlanArchetypeDef a, AttachmentNode node, Set<PartType> available) {
        if (!available.contains(node.partType))
            throw new IllegalStateException("Archetype '" + a.id + "' needs a part of type "
                + node.partType + " but none exists in the library");
        if (node.repeat > 1 && node.continuationSocketId == null)
            throw new IllegalStateException("Archetype '" + a.id + "' node with repeat>1 needs continuationSocketId");
        for (AttachmentNode c : node.children) checkNode(a, c, available);
    }

    /** Parts of a type eligible for a body plan (bodyPlans empty = any), in stable id order. */
    public List<CreaturePartDef> partsFor(PartType type, BodyPlan plan) {
        List<CreaturePartDef> out = new ArrayList<>();
        for (CreaturePartDef p : parts.values())
            if (p.partType == type && (p.bodyPlans.isEmpty() || p.bodyPlans.contains(plan)))
                out.add(p);
        out.sort((x, y) -> x.id.compareTo(y.id)); // determinism: never depend on map order
        return out;
    }

    public CreaturePartDef getPart(String id)            { return parts.get(id); }
    public BodyPlanArchetypeDef getArchetype(String id)  { return archetypes.get(id); }
    public java.util.Collection<BodyPlanArchetypeDef> allArchetypes() { return archetypes.values(); }
}
