package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class CameraSystem extends IteratingSystem {

    private static final float EYE_HEIGHT_LERP_SPEED = 10f;
    private static final float LANDING_DIP_DECAY_SPEED = 8f;
    private static final float WALK_SPEED_REF = 3.5f;
    private static final float HEAD_BOB_MIN_SPEED = 0.5f;
    private static final float MAX_LANDING_DIP = 0.15f;
    private static final float LANDING_DIP_FACTOR = 0.02f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);

    private PerspectiveCamera camera;
    private boolean wasGrounded;

    public CameraSystem() {
        super(Family.all(TransformComponent.class, FPSCameraComponent.class, MovementStateComponent.class).get(), 4);
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (camera == null) return;

        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);

        float targetEyeHeight = state.isCrouching ? cam.crouchEyeHeight : cam.eyeHeight;
        cam.currentEyeHeight = MathUtils.lerp(cam.currentEyeHeight, targetEyeHeight,
            EYE_HEIGHT_LERP_SPEED * deltaTime);

        float camX = transform.position.x;
        float camY = transform.position.y + cam.currentEyeHeight;
        float camZ = transform.position.z;

        if (state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float vOffset = MathUtils.sin(cam.headBobPhase) * cam.headBobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * cam.headBobAmplitude * 0.5f;
            camY += vOffset;

            float yawRad = cam.yawAngle * MathUtils.degreesToRadians;
            camX += MathUtils.cos(yawRad) * hOffset;
            camZ += -MathUtils.sin(yawRad) * hOffset;
        } else {
            cam.headBobPhase = 0;
        }

        if (!wasGrounded && state.isGrounded) {
            cam.landingDipAmount = Math.min(MAX_LANDING_DIP, state.fallVelocity * LANDING_DIP_FACTOR);
        }
        if (cam.landingDipAmount > 0) {
            camY -= cam.landingDipAmount;
            cam.landingDipAmount = Math.max(0, cam.landingDipAmount - LANDING_DIP_DECAY_SPEED * deltaTime);
        }

        camera.position.set(camX, camY, camZ);

        float pitchRad = cam.pitchAngle * MathUtils.degreesToRadians;
        float yawRad = cam.yawAngle * MathUtils.degreesToRadians;

        camera.direction.set(
            -MathUtils.sin(yawRad) * MathUtils.cos(pitchRad),
            MathUtils.sin(pitchRad),
            -MathUtils.cos(yawRad) * MathUtils.cos(pitchRad)
        ).nor();

        camera.up.set(Vector3.Y);
        camera.update();

        wasGrounded = state.isGrounded;
    }
}
