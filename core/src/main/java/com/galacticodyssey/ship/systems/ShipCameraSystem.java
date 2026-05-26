package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.PilotSeatComponent;
import com.galacticodyssey.ship.components.ShipDataComponent;

public class ShipCameraSystem extends EntitySystem {

    public enum CameraMode { COCKPIT, CHASE }

    private PerspectiveCamera camera;
    private CameraMode cameraMode = CameraMode.COCKPIT;

    private ImmutableArray<Entity> playerEntities;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<ShipDataComponent> dataMapper =
        ComponentMapper.getFor(ShipDataComponent.class);
    private final ComponentMapper<PilotSeatComponent> seatMapper =
        ComponentMapper.getFor(PilotSeatComponent.class);

    private static final float COCKPIT_LAG = 8f;
    private static final float CHASE_POS_LERP = 4f;
    private static final float CHASE_ROT_LERP = 6f;

    private final Vector3 currentChasePos = new Vector3();
    private final Matrix4 tempMat = new Matrix4();
    private final Vector3 tempVec = new Vector3();

    public ShipCameraSystem() {
        super(4);
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (camera == null || playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        PlayerInputComponent input = inputMapper.get(player);
        if (input != null && input.cameraTogglePressed) {
            cameraMode = (cameraMode == CameraMode.COCKPIT) ? CameraMode.CHASE : CameraMode.COCKPIT;
            input.cameraTogglePressed = false;
        }

        Entity ship = state.currentShip;
        TransformComponent shipTransform = transformMapper.get(ship);
        if (shipTransform == null) return;

        switch (cameraMode) {
            case COCKPIT:
                updateCockpitCamera(ship, shipTransform, deltaTime);
                break;
            case CHASE:
                updateChaseCamera(ship, shipTransform, deltaTime);
                break;
        }
    }

    private void updateCockpitCamera(Entity ship, TransformComponent shipTransform, float deltaTime) {
        PilotSeatComponent seat = seatMapper.get(ship);
        if (seat == null) return;

        tempMat.set(shipTransform.position, shipTransform.rotation);

        // Transform seat's local interior position into world space
        tempVec.set(seat.interiorPosition).mul(tempMat);
        camera.position.set(tempVec);

        // Compute ship forward in world space and lerp camera direction toward it
        Vector3 shipForward = new Vector3(0, 0, -1).rot(tempMat).nor();
        camera.direction.lerp(shipForward, COCKPIT_LAG * deltaTime).nor();

        camera.up.set(0, 1, 0).rot(tempMat).nor();
        camera.update();
    }

    private void updateChaseCamera(Entity ship, TransformComponent shipTransform, float deltaTime) {
        ShipDataComponent data = dataMapper.get(ship);

        float followDist = 15f;
        if (data != null && data.blueprint != null) {
            switch (data.blueprint.sizeClass) {
                case SMALL:  followDist = 15f; break;
                case MEDIUM: followDist = 30f; break;
                case LARGE:  followDist = 60f; break;
            }
        }

        tempMat.set(shipTransform.position, shipTransform.rotation);

        // Desired chase position: behind and above the ship in local space, then world-space transform
        Vector3 targetPos = new Vector3(0, followDist * 0.3f, followDist).mul(tempMat);
        currentChasePos.lerp(targetPos, CHASE_POS_LERP * deltaTime);
        camera.position.set(currentChasePos);

        camera.lookAt(shipTransform.position);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    public CameraMode getCameraMode() { return cameraMode; }
}
