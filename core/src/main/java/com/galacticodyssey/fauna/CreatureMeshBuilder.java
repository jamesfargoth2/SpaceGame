package com.galacticodyssey.fauna;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.fauna.assembly.AssembledNode;
import com.galacticodyssey.fauna.geometry.PartGeometryProvider;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;

import java.util.ArrayList;
import java.util.List;

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
            Model model = providerFor(node.part.geometry).buildPartModel(node.part.geometry);
            ownedModels.add(model);
            ModelInstance inst = new ModelInstance(model);
            Matrix4 m = new Matrix4(node.worldTransform);
            m.scl(node.scale);
            inst.transform.set(m);
            out.add(inst);
        }
        return out;
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
