package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.shipbuilder.RoomDesign;
import com.galacticodyssey.shipbuilder.ShipDesignValidator;

import java.util.*;

public class CorridorPreview implements Disposable {
    private final Array<ModelInstance> corridorInstances = new Array<>();
    private final Array<Model> corridorModels = new Array<>();
    private final ShipDesignValidator validator = new ShipDesignValidator();

    public void update(List<RoomDesign> rooms) {
        dispose();
        ModelBuilder mb = new ModelBuilder();
        Material mat = new Material(
            ColorAttribute.createDiffuse(new Color(0.5f, 0.5f, 0.5f, 0.2f)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        );

        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                if (validator.areAdjacent(rooms.get(i), rooms.get(j))) {
                    RoomDesign a = rooms.get(i);
                    RoomDesign b = rooms.get(j);
                    float cx = (a.gridX + a.sizeX / 2f + b.gridX + b.sizeX / 2f) / 2f;
                    float cy = 0.5f;
                    float cz = (a.gridZ + a.sizeZ / 2f + b.gridZ + b.sizeZ / 2f) / 2f;
                    Model m = mb.createBox(1, 1, 1, mat,
                        VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    ModelInstance inst = new ModelInstance(m);
                    inst.transform.setToTranslation(cx, cy, cz);
                    corridorModels.add(m);
                    corridorInstances.add(inst);
                }
            }
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : corridorInstances) {
            batch.render(inst, env);
        }
    }

    @Override
    public void dispose() {
        for (Model m : corridorModels) m.dispose();
        corridorModels.clear();
        corridorInstances.clear();
    }
}
