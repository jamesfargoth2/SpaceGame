package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.RoomDesign;

public class GhostVolume implements Disposable {
    private Model model;
    private ModelInstance instance;
    private RoomType roomType;
    private int sizeX, sizeY, sizeZ;
    private int gridX, gridY, gridZ;
    private int rotation;
    private boolean valid;
    private boolean active;

    private static final Color VALID_COLOR = new Color(0.2f, 0.8f, 0.2f, 0.3f);
    private static final Color INVALID_COLOR = new Color(0.8f, 0.2f, 0.2f, 0.3f);

    public void activate(RoomType type) {
        this.roomType = type;
        this.sizeX = type.minSizeX;
        this.sizeY = type.minSizeY;
        this.sizeZ = type.minSizeZ;
        this.rotation = 0;
        this.active = true;
        rebuildModel();
    }

    public void deactivate() {
        active = false;
        if (model != null) { model.dispose(); model = null; }
        instance = null;
    }

    public void setGridPosition(int x, int y, int z) {
        this.gridX = x;
        this.gridY = y;
        this.gridZ = z;
        if (instance != null) {
            instance.transform.setToTranslation(x, y, z);
        }
    }

    public void rotate90() {
        rotation = (rotation + 1) % 4;
        if (rotation % 2 == 1) {
            int tmp = sizeX;
            sizeX = sizeZ;
            sizeZ = tmp;
        } else {
            sizeX = roomType.minSizeX;
            sizeZ = roomType.minSizeZ;
        }
        rebuildModel();
    }

    public void setValid(boolean valid) {
        this.valid = valid;
        if (instance != null) {
            Color c = valid ? VALID_COLOR : INVALID_COLOR;
            instance.materials.get(0).set(ColorAttribute.createDiffuse(c));
        }
    }

    public RoomDesign toRoomDesign() {
        return new RoomDesign(roomType, gridX, gridY, gridZ, sizeX, sizeY, sizeZ);
    }

    private void rebuildModel() {
        if (model != null) model.dispose();
        ModelBuilder mb = new ModelBuilder();
        Color c = valid ? VALID_COLOR : INVALID_COLOR;
        Material mat = new Material(
            ColorAttribute.createDiffuse(c),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        );
        model = mb.createBox(sizeX, sizeY, sizeZ, mat,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instance = new ModelInstance(model);
        instance.transform.setToTranslation(
            gridX + sizeX / 2f, gridY + sizeY / 2f, gridZ + sizeZ / 2f
        );
    }

    public void render(ModelBatch batch, Environment env) {
        if (active && instance != null) {
            batch.render(instance, env);
        }
    }

    public boolean isActive() { return active; }
    public RoomType getRoomType() { return roomType; }

    @Override
    public void dispose() {
        if (model != null) model.dispose();
    }
}
