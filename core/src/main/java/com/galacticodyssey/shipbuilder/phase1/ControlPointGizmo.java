package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

public class ControlPointGizmo implements Disposable {
    private static final float SPHERE_RADIUS = 0.3f;
    private static final float PICK_RADIUS = 0.5f;

    private Model sphereModel;
    private final Array<ModelInstance> instances = new Array<>();
    private int selectedIndex = -1;
    private boolean dragging;
    private final Vector3 dragPlaneNormal = new Vector3();
    private final Plane dragPlane = new Plane();
    private final Vector3 tmpIntersection = new Vector3();

    public void build() {
        if (sphereModel != null) sphereModel.dispose();
        ModelBuilder mb = new ModelBuilder();
        sphereModel = mb.createSphere(SPHERE_RADIUS * 2, SPHERE_RADIUS * 2, SPHERE_RADIUS * 2, 12, 8,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public void updatePositions(List<Vector3> spinePoints) {
        instances.clear();
        for (Vector3 point : spinePoints) {
            ModelInstance inst = new ModelInstance(sphereModel);
            inst.transform.setToTranslation(point);
            instances.add(inst);
        }
        if (selectedIndex >= 0 && selectedIndex < instances.size) {
            instances.get(selectedIndex).materials.get(0)
                .set(ColorAttribute.createDiffuse(Color.YELLOW));
        }
    }

    public int pick(Ray ray, List<Vector3> spinePoints) {
        float closestDist = Float.MAX_VALUE;
        int closestIdx = -1;
        for (int i = 0; i < spinePoints.size(); i++) {
            Vector3 point = spinePoints.get(i);
            float dist = ray.origin.dst(point);
            Vector3 projected = new Vector3();
            float t = ray.direction.dot(new Vector3(point).sub(ray.origin));
            projected.set(ray.direction).scl(t).add(ray.origin);
            float pickDist = projected.dst(point);
            if (pickDist < PICK_RADIUS && dist < closestDist) {
                closestDist = dist;
                closestIdx = i;
            }
        }
        selectedIndex = closestIdx;
        return closestIdx;
    }

    public void beginDrag(Camera camera) {
        dragging = true;
        dragPlaneNormal.set(camera.direction).scl(-1);
    }

    public Vector3 drag(Ray ray, Vector3 currentPosition) {
        if (!dragging || selectedIndex < 0) return currentPosition;
        dragPlane.set(currentPosition, dragPlaneNormal);
        if (Intersector.intersectRayPlane(ray, dragPlane, tmpIntersection)) {
            return tmpIntersection;
        }
        return currentPosition;
    }

    public void endDrag() {
        dragging = false;
    }

    public int getSelectedIndex() { return selectedIndex; }
    public void clearSelection() { selectedIndex = -1; }
    public boolean isDragging() { return dragging; }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : instances) {
            batch.render(inst, env);
        }
    }

    @Override
    public void dispose() {
        if (sphereModel != null) sphereModel.dispose();
    }
}
