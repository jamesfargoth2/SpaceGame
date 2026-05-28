package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Chase camera for the driven vehicle. Active only while DRIVING. Mirrors ShipCameraSystem CHASE. */
public class VehicleCameraSystem extends EntitySystem {

    public static final int PRIORITY = 4;
    private static final float BACK = 9f, UP = 4f, LERP = 6f;

    private final PerspectiveCamera camera;
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> players;
    private final Matrix4 tmpMat = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();
    private final Vector3 back = new Vector3();
    private final Vector3 desiredPos = new Vector3();
    private boolean active;

    public VehicleCameraSystem(PerspectiveCamera camera) {
        super(PRIORITY);
        this.camera = camera;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (players.size() == 0) { active = false; return; }
        Entity player = players.first();
        PlayerStateComponent state = STATE_M.get(player);
        if (state.currentMode != PlayerMode.DRIVING || state.currentVehicle == null) {
            active = false;
            return;
        }
        TransformComponent vt = TRANSFORM_M.get(state.currentVehicle);
        if (vt == null) return;

        tmpMat.set(vt.position, vt.rotation);
        tmpMat.getRotation(tmpQuat);
        back.set(0f, 0f, 1f).mul(tmpQuat).nor(); // +Z is "behind" (forward is -Z)
        desiredPos.set(vt.position).mulAdd(back, BACK).add(0f, UP, 0f);

        float a = active ? Math.min(1f, LERP * deltaTime) : 1f;
        camera.position.lerp(desiredPos, a);
        camera.lookAt(vt.position);
        camera.up.set(0f, 1f, 0f);
        camera.update();
        active = true;
    }
}
