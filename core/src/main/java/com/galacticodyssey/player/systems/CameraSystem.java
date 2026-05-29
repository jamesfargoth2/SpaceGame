package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.ADSComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class CameraSystem extends IteratingSystem {

    private static final float EYE_HEIGHT_LERP_SPEED = 10f;
    private static final float LANDING_DIP_DECAY_SPEED = 8f;
    private static final float WALK_SPEED_REF = 3.5f;
    private static final float HEAD_BOB_MIN_SPEED = 0.5f;
    private static final float MAX_LANDING_DIP = 0.15f;
    private static final float LANDING_DIP_FACTOR = 0.02f;
    private static final float RECOIL_RECOVERY_SPEED = 8f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<ADSComponent> adsMapper =
        ComponentMapper.getFor(ADSComponent.class);

    private PerspectiveCamera camera;
    private boolean wasGrounded;

    private float recoilPitch;
    private float recoilYaw;

    public CameraSystem() {
        super(Family.all(TransformComponent.class, FPSCameraComponent.class, MovementStateComponent.class).get(), 4);
    }

    public CameraSystem(EventBus eventBus) {
        this();
        eventBus.subscribe(RecoilEvent.class, this::onRecoil);
    }

    private void onRecoil(RecoilEvent event) {
        recoilYaw += event.recoilOffset.x;
        recoilPitch += event.recoilOffset.y;
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (camera == null) return;

        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null
                && (playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING
                || playerState.currentMode == PlayerStateComponent.PlayerMode.DRIVING)) {
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

        // Lean processing
        PlayerInputComponent input = inputMapper.get(entity);
        float targetLean = 0f;
        if (input != null) {
            if (input.leanLeft) targetLean = cam.maxLeanAngle;
            if (input.leanRight) targetLean = -cam.maxLeanAngle;
        }
        cam.leanAngle = MathUtils.lerp(cam.leanAngle, targetLean, cam.leanSpeed * deltaTime);
        if (Math.abs(cam.leanAngle) < 0.01f) cam.leanAngle = 0f;

        float leanFraction = cam.leanAngle / cam.maxLeanAngle;
        float yawRadForLean = cam.yawAngle * MathUtils.degreesToRadians;
        // Left-direction unit vector: camera-right is (+cos, 0, -sin), so left is (-cos, 0, +sin)
        float sideX = -MathUtils.cos(yawRadForLean);
        float sideZ =  MathUtils.sin(yawRadForLean);

        // Shift the body sideways so the rendered model follows; physics capsule stays centred
        float bodyShift = leanFraction * cam.leanBodyShift;
        transform.position.x += sideX * bodyShift;
        transform.position.z += sideZ * bodyShift;
        camX += sideX * bodyShift;
        camZ += sideZ * bodyShift;

        // Remaining eye/head lean offset past the body pivot
        float eyeExtra = leanFraction * (cam.leanHorizontalOffset - cam.leanBodyShift);
        camX += sideX * eyeExtra;
        camZ += sideZ * eyeExtra;

        cam.worldEyePos.set(camX, camY, camZ);
        camera.position.set(camX, camY, camZ);

        // Apply recoil offsets on top of the base camera angles.
        float effectivePitch = cam.pitchAngle + recoilPitch;
        float effectiveYaw = cam.yawAngle + recoilYaw;

        float pitchRad = effectivePitch * MathUtils.degreesToRadians;
        float yawRad = effectiveYaw * MathUtils.degreesToRadians;

        camera.direction.set(
            -MathUtils.sin(yawRad) * MathUtils.cos(pitchRad),
            MathUtils.sin(pitchRad),
            -MathUtils.cos(yawRad) * MathUtils.cos(pitchRad)
        ).nor();

        // Apply lean roll to camera up vector
        float leanRad = cam.leanAngle * MathUtils.degreesToRadians;
        float rightX = -MathUtils.cos(yawRad);
        float rightZ = MathUtils.sin(yawRad);
        camera.up.set(
            rightX * MathUtils.sin(leanRad),
            MathUtils.cos(leanRad),
            rightZ * MathUtils.sin(leanRad)
        ).nor();

        // Apply ADS zoom: lerp FOV toward baseFov * zoomMultiplier as adsProgress reaches 1.
        ADSComponent ads = adsMapper.get(entity);
        if (ads != null) {
            float targetFov = cam.baseFov * MathUtils.lerp(1f, ads.zoomMultiplier, ads.adsProgress);
            camera.fieldOfView = MathUtils.lerp(camera.fieldOfView, targetFov, 10f * deltaTime);
        }

        camera.update();

        // Decay recoil back toward zero.
        float decay = RECOIL_RECOVERY_SPEED * deltaTime;
        recoilPitch = recoilPitch > 0
            ? Math.max(0f, recoilPitch - decay)
            : Math.min(0f, recoilPitch + decay);
        recoilYaw = recoilYaw > 0
            ? Math.max(0f, recoilYaw - decay)
            : Math.min(0f, recoilYaw + decay);

        wasGrounded = state.isGrounded;
    }
}
