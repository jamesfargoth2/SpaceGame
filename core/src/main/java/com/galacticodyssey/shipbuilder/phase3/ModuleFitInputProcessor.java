package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.galacticodyssey.shipbuilder.DrydockScene;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class ModuleFitInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final HardpointMarkerRenderer hardpointRenderer;
    private final DrydockScene scene;
    private HardpointClickCallback clickCallback;

    private boolean exteriorMode = true;
    private float orbitAzimuth = 0f, orbitElevation = 30f, orbitDistance = 25f;
    private final Vector3 orbitTarget = new Vector3(0, 0, 5);
    private boolean orbiting;
    private int lastX, lastY;

    public interface HardpointClickCallback {
        void onHardpointClicked(int index, HardpointMarkerRenderer.HardpointDef def);
    }

    public ModuleFitInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                    HardpointMarkerRenderer hardpointRenderer, DrydockScene scene) {
        this.camera = camera;
        this.design = design;
        this.hardpointRenderer = hardpointRenderer;
        this.scene = scene;
        updateCameraOrbit();
    }

    public void setClickCallback(HardpointClickCallback callback) {
        this.clickCallback = callback;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.V) {
            exteriorMode = !exteriorMode;
            if (exteriorMode) updateCameraOrbit();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        lastX = screenX;
        lastY = screenY;
        if (button == 1 && exteriorMode) {
            orbiting = true;
            return true;
        }
        if (button == 0 && exteriorMode) {
            Ray ray = camera.getPickRay(screenX, screenY);
            int picked = hardpointRenderer.pick(ray);
            if (picked >= 0 && clickCallback != null) {
                clickCallback.onHardpointClicked(picked, hardpointRenderer.getHardpoint(picked));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (orbiting) {
            int dx = screenX - lastX;
            int dy = screenY - lastY;
            lastX = screenX;
            lastY = screenY;
            orbitAzimuth -= dx * 0.5f;
            orbitElevation = MathUtils.clamp(orbitElevation + dy * 0.5f, -89f, 89f);
            updateCameraOrbit();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == 1) orbiting = false;
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (exteriorMode) {
            orbitDistance = MathUtils.clamp(orbitDistance + amountY * 2f, 5f, 100f);
            updateCameraOrbit();
            return true;
        }
        return false;
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

    public boolean isExteriorMode() { return exteriorMode; }
}
