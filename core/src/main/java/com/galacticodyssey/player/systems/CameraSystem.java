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

    private PerspectiveCamera camera;
    private boolean wasGrounded;

    private float recoilPitch;
    private float recoilYaw;

    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 pivot = new Vector3();
    private final Vector3 dir = new Vector3();

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
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);

        PlayerInputComponent input = inputMapper.get(entity);
        if (input != null && input.scrollDelta != 0) {
            cam.targetCameraDistance = MathUtils.clamp(
                cam.targetCameraDistance + input.scrollDelta * cam.zoomStep,
                0f, cam.maxCameraDistance);
            input.scrollDelta = 0;
        }

        cam.currentCameraDistance = MathUtils.lerp(cam.currentCameraDistance,
            cam.targetCameraDistance, cam.zoomLerpSpeed * deltaTime);
        if (Math.abs(cam.currentCameraDistance - cam.targetCameraDistance) < 0.01f) {
            cam.currentCameraDistance = cam.targetCameraDistance;
        }

        boolean firstPerson = cam.currentCameraDistance < 0.1f;

        Vector3 up = cam.localUp;

        buildTangentFrame(up, cam.yawAngle);

        float targetEyeHeight = state.isCrouching ? cam.crouchEyeHeight : cam.eyeHeight;
        cam.currentEyeHeight = MathUtils.lerp(cam.currentEyeHeight, targetEyeHeight,
            EYE_HEIGHT_LERP_SPEED * deltaTime);

        pivot.set(up).scl(cam.currentEyeHeight).add(transform.position);

        if (firstPerson && state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float vOffset = MathUtils.sin(cam.headBobPhase) * cam.headBobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * cam.headBobAmplitude * 0.5f;
            pivot.add(up.x * vOffset, up.y * vOffset, up.z * vOffset);
            pivot.add(localRight.x * hOffset, localRight.y * hOffset, localRight.z * hOffset);
        } else if (firstPerson) {
            cam.headBobPhase = 0;
        }

        if (!wasGrounded && state.isGrounded && firstPerson) {
            cam.landingDipAmount = Math.min(MAX_LANDING_DIP, state.fallVelocity * LANDING_DIP_FACTOR);
        }
        if (cam.landingDipAmount > 0 && firstPerson) {
            pivot.add(-up.x * cam.landingDipAmount, -up.y * cam.landingDipAmount, -up.z * cam.landingDipAmount);
            cam.landingDipAmount = Math.max(0, cam.landingDipAmount - LANDING_DIP_DECAY_SPEED * deltaTime);
        }

        // Apply recoil offsets on top of the base camera angles.
        float effectivePitch = cam.pitchAngle + recoilPitch;
        float effectiveYaw = cam.yawAngle + recoilYaw;

        buildTangentFrame(up, effectiveYaw);

        float pitchRad = effectivePitch * MathUtils.degreesToRadians;
        float cosPitch = MathUtils.cos(pitchRad);
        float sinPitch = MathUtils.sin(pitchRad);

        float dirX = -MathUtils.sin(yawRad) * MathUtils.cos(pitchRad);
        float dirY =  MathUtils.sin(pitchRad);
        float dirZ = -MathUtils.cos(yawRad) * MathUtils.cos(pitchRad);

        // 3rd-person zoom: scroll wheel adjusts target distance.
        PlayerInputComponent inputComp = inputMapper.get(entity);
        if (inputComp != null && inputComp.scrollDelta != 0) {
            cam.targetCameraDistance = MathUtils.clamp(
                cam.targetCameraDistance + inputComp.scrollDelta * cam.zoomStep,
                0f, cam.maxCameraDistance);
        }
        cam.currentCameraDistance = MathUtils.lerp(
            cam.currentCameraDistance, cam.targetCameraDistance, cam.zoomLerpSpeed * deltaTime);

        // Pull camera back along negative look direction when in 3rd-person.
        camX -= dirX * cam.currentCameraDistance;
        camY -= dirY * cam.currentCameraDistance;
        camZ -= dirZ * cam.currentCameraDistance;

        camera.position.set(camX, camY, camZ);
        camera.direction.set(dirX, dirY, dirZ).nor();

        if (firstPerson) {
            camera.position.set(pivot);
        } else {
            float dist = cam.currentCameraDistance;
            camera.position.set(
                pivot.x - dir.x * dist + up.x * dist * 0.15f,
                pivot.y - dir.y * dist + up.y * dist * 0.15f,
                pivot.z - dir.z * dist + up.z * dist * 0.15f);
        }

        camera.direction.set(dir);
        camera.up.set(up);
        camera.update();

        float decay = RECOIL_RECOVERY_SPEED * deltaTime;
        recoilPitch = recoilPitch > 0
            ? Math.max(0f, recoilPitch - decay)
            : Math.min(0f, recoilPitch + decay);
        recoilYaw = recoilYaw > 0
            ? Math.max(0f, recoilYaw - decay)
            : Math.min(0f, recoilYaw + decay);

        wasGrounded = state.isGrounded;
    }

    private void buildTangentFrame(Vector3 up, float yawAngle) {
        Vector3 ref = Math.abs(up.y) < 0.999f ? Vector3.Y : Vector3.Z;
        localRight.set(ref).crs(up).nor();
        localForward.set(up).crs(localRight).nor();

        float yawRad = yawAngle * MathUtils.degreesToRadians;
        float cosYaw = MathUtils.cos(yawRad);
        float sinYaw = MathUtils.sin(yawRad);

        float fwdX = localForward.x * cosYaw + localRight.x * sinYaw;
        float fwdY = localForward.y * cosYaw + localRight.y * sinYaw;
        float fwdZ = localForward.z * cosYaw + localRight.z * sinYaw;

        float rgtX = -localForward.x * sinYaw + localRight.x * cosYaw;
        float rgtY = -localForward.y * sinYaw + localRight.y * cosYaw;
        float rgtZ = -localForward.z * sinYaw + localRight.z * cosYaw;

        localForward.set(fwdX, fwdY, fwdZ);
        localRight.set(rgtX, rgtY, rgtZ);
    }
}
