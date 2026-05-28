package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.galacticodyssey.shipbuilder.DrydockScene;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class HullSculptInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final DrydockScene scene;
    private final ControlPointGizmo gizmo;

    private float orbitAzimuth = 0f;
    private float orbitElevation = 30f;
    private float orbitDistance = 25f;
    private final Vector3 orbitTarget = new Vector3(0, 0, 5);
    private int lastX, lastY;
    private boolean orbiting;

    public HullSculptInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                     DrydockScene scene, ControlPointGizmo gizmo) {
        this.camera = camera;
        this.design = design;
        this.scene = scene;
        this.gizmo = gizmo;
        updateCameraOrbit();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        lastX = screenX;
        lastY = screenY;
        if (button == 1) {
            orbiting = true;
            return true;
        }
        if (button == 0) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int picked = gizmo.pick(ray, design.hull.spinePoints);
            if (picked >= 0) {
                gizmo.beginDrag(camera);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        int dx = screenX - lastX;
        int dy = screenY - lastY;
        lastX = screenX;
        lastY = screenY;

        if (orbiting) {
            orbitAzimuth -= dx * 0.5f;
            orbitElevation = MathUtils.clamp(orbitElevation + dy * 0.5f, -89f, 89f);
            updateCameraOrbit();
            return true;
        }

        if (gizmo.isDragging()) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int idx = gizmo.getSelectedIndex();
            Vector3 current = design.hull.spinePoints.get(idx);
            Vector3 newPos = gizmo.drag(ray, current);
            design.hull.moveSpinePoint(idx, newPos);
            scene.markMeshDirty();
            gizmo.updatePositions(design.hull.spinePoints);
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) orbiting = false;
        if (gizmo.isDragging()) gizmo.endDrag();
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        orbitDistance = MathUtils.clamp(orbitDistance + amountY * 2f, 5f, 100f);
        updateCameraOrbit();
        return true;
    }

    private void updateCameraOrbit() {
        float azRad = orbitAzimuth * MathUtils.degreesToRadians;
        float elRad = orbitElevation * MathUtils.degreesToRadians;
        float cosEl = MathUtils.cos(elRad);
        camera.position.set(
            orbitTarget.x + orbitDistance * cosEl * MathUtils.sin(azRad),
            orbitTarget.y + orbitDistance * MathUtils.sin(elRad),
            orbitTarget.z + orbitDistance * cosEl * MathUtils.cos(azRad)
        );
        camera.lookAt(orbitTarget);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    public void setOrbitTarget(Vector3 target) {
        orbitTarget.set(target);
        updateCameraOrbit();
    }
}
