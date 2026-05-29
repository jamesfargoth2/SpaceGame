package com.galacticodyssey.fauna;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.geometry.PartGeometryProvider;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;
import com.galacticodyssey.fauna.geometry.ProceduralMeshData;
import com.galacticodyssey.fauna.geometry.ProceduralPartMesher;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.CreatureRig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes a {@link CreatureSpec} into renderable {@link ModelInstance}s — one per assembled
 * node, transformed by the node's worldTransform and scale. Thin GL translation layer.
 */
public final class CreatureMeshBuilder implements Disposable {
    private final List<PartGeometryProvider> providers = new ArrayList<>();
    private final Array<Model> ownedModels = new Array<>();

    public CreatureMeshBuilder(PartGeometryProvider... providers) {
        for (PartGeometryProvider p : providers) this.providers.add(p);
    }

    /** Returns one ModelInstance per part, ready to render. */
    public Array<ModelInstance> build(CreatureSpec spec) {
        Array<ModelInstance> out = new Array<>();
        for (AssembledNode node : spec.allNodes) {
            PartGeometryProvider prov = providerFor(node.part.geometry);
            Model model = prov.buildPartModel(node.part.geometry);
            if (prov.ownsBuiltModels()) ownedModels.add(model);
            ModelInstance inst = new ModelInstance(model);
            Matrix4 m = new Matrix4(node.worldTransform);
            m.scl(node.scale);
            inst.transform.set(m);
            out.add(inst);
        }
        return out;
    }

    /** Returns a single ModelInstance with a node hierarchy driven by the rig. */
    public ModelInstance buildSkinned(CreatureSpec spec, CreatureRig rig) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();

        Map<Integer, Node> nodeMap = new HashMap<>();
        ProceduralPartMesher mesher = new ProceduralPartMesher();

        for (int i = 0; i < spec.allNodes.size(); i++) {
            AssembledNode an = spec.allNodes.get(i);

            Node node = mb.node();
            node.id = rig.getBone(i).name;

            Material mat = new Material(ColorAttribute.createDiffuse(Color.GRAY));
            MeshPartBuilder mpb = mb.part("mesh_" + i, GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal, mat);

            ProceduralMeshData data = mesher.build(an.part.geometry);
            float[] p = data.positions;
            for (int t = 0; t < data.indices.length; t += 3) {
                int a = data.indices[t] & 0xFFFF;
                int b = data.indices[t + 1] & 0xFFFF;
                int c = data.indices[t + 2] & 0xFFFF;
                mpb.triangle(
                    new Vector3(p[a*3], p[a*3+1], p[a*3+2]),
                    new Vector3(p[b*3], p[b*3+1], p[b*3+2]),
                    new Vector3(p[c*3], p[c*3+1], p[c*3+2]));
            }

            nodeMap.put(i, node);
        }

        Model model = mb.end();
        ownedModels.add(model);

        // Rearrange flat node list into bone hierarchy
        for (int i = 0; i < rig.boneCount(); i++) {
            Node node = nodeMap.get(i);
            Bone bone = rig.getBone(i);
            node.localTransform.set(bone.bindPose);
            node.localTransform.scl(spec.allNodes.get(i).scale);

            if (bone.parentIndex >= 0) {
                Node parent = nodeMap.get(bone.parentIndex);
                model.nodes.removeValue(node, true);
                parent.addChild(node);
            }
        }

        model.calculateTransforms();
        return new ModelInstance(model);
    }

    private PartGeometryProvider providerFor(PartGeometrySpec spec) {
        for (PartGeometryProvider p : providers) if (p.supports(spec)) return p;
        throw new IllegalStateException("No geometry provider supports spec kind " + spec.kind);
    }

    @Override public void dispose() {
        for (Model m : ownedModels) m.dispose();
        ownedModels.clear();
        for (PartGeometryProvider p : providers) p.dispose();
    }
}
