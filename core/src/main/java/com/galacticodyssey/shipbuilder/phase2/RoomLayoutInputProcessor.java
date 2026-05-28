package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.RoomType;
import com.galacticodyssey.shipbuilder.*;

public class RoomLayoutInputProcessor extends InputAdapter {
    private final PerspectiveCamera camera;
    private final ShipDesign design;
    private final ShipDesignValidator validator;
    private final GhostVolume ghost;
    private final CorridorPreview corridorPreview;
    private final DrydockScene scene;
    private boolean[][][] hullMask;

    private float yaw, pitch;
    private final Vector3 position = new Vector3(0, 1.7f, 0);
    private static final float MOVE_SPEED = 5f;
    private static final float MOUSE_SENSITIVITY = 0.15f;

    private boolean forward, backward, left, right;

    public RoomLayoutInputProcessor(PerspectiveCamera camera, ShipDesign design,
                                     ShipDesignValidator validator, GhostVolume ghost,
                                     CorridorPreview corridorPreview, DrydockScene scene) {
        this.camera = camera;
        this.design = design;
        this.validator = validator;
        this.ghost = ghost;
        this.corridorPreview = corridorPreview;
        this.scene = scene;
    }

    public void setHullMask(boolean[][][] mask) {
        this.hullMask = mask;
    }

    public void update(float delta) {
        Vector3 dir = new Vector3(camera.direction).nor();
        dir.y = 0;
        dir.nor();
        Vector3 strafe = new Vector3(dir).crs(Vector3.Y).nor();

        if (forward) position.mulAdd(dir, MOVE_SPEED * delta);
        if (backward) position.mulAdd(dir, -MOVE_SPEED * delta);
        if (left) position.mulAdd(strafe, -MOVE_SPEED * delta);
        if (right) position.mulAdd(strafe, MOVE_SPEED * delta);

        camera.position.set(position);
        camera.direction.set(
            MathUtils.cosDeg(pitch) * MathUtils.sinDeg(yaw),
            MathUtils.sinDeg(pitch),
            MathUtils.cosDeg(pitch) * MathUtils.cosDeg(yaw)
        ).nor();
        camera.up.set(Vector3.Y);
        camera.update();

        if (ghost.isActive()) {
            Vector3 lookAt = new Vector3(camera.direction).scl(5f).add(camera.position);
            int gx = MathUtils.floor(lookAt.x);
            int gy = 0;
            int gz = MathUtils.floor(lookAt.z);
            ghost.setGridPosition(gx, gy, gz);
            RoomDesign candidate = ghost.toRoomDesign();
            ghost.setValid(validator.canPlaceRoom(design, candidate, hullMask));
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.W: forward = true; return true;
            case Input.Keys.S: backward = true; return true;
            case Input.Keys.A: left = true; return true;
            case Input.Keys.D: right = true; return true;
            case Input.Keys.NUM_1: ghost.activate(RoomType.COCKPIT); return true;
            case Input.Keys.NUM_2: ghost.activate(RoomType.ENGINE_ROOM); return true;
            case Input.Keys.NUM_3: ghost.activate(RoomType.CREW_QUARTERS); return true;
            case Input.Keys.NUM_4: ghost.activate(RoomType.MEDBAY); return true;
            case Input.Keys.NUM_5: ghost.activate(RoomType.ARMORY); return true;
            case Input.Keys.NUM_6: ghost.activate(RoomType.CARGO_BAY); return true;
            // NOTE: No NUM_7/BRIG — RoomType enum does not have BRIG
            case Input.Keys.ESCAPE: ghost.deactivate(); return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.W: forward = false; return true;
            case Input.Keys.S: backward = false; return true;
            case Input.Keys.A: left = false; return true;
            case Input.Keys.D: right = false; return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        yaw -= screenX * MOUSE_SENSITIVITY;
        pitch = MathUtils.clamp(pitch - screenY * MOUSE_SENSITIVITY, -89f, 89f);
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == 0 && ghost.isActive()) {
            RoomDesign candidate = ghost.toRoomDesign();
            if (validator.canPlaceRoom(design, candidate, hullMask)) {
                design.addRoom(candidate);
                corridorPreview.update(design.rooms);
                ghost.deactivate();
                return true;
            }
        }
        if (button == 1) {
            ghost.deactivate();
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (ghost.isActive()) {
            ghost.rotate90();
            return true;
        }
        return false;
    }

    public void setPosition(Vector3 pos) { position.set(pos); }
}
