package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;

public class PlayerMovementSystem extends IteratingSystem {

    private static final float WALK_SPEED = 3.5f;
    private static final float SPRINT_SPEED = 6.0f;
    private static final float CROUCH_SPEED = 1.5f;
    private static final float JUMP_IMPULSE = 5.0f;
    private static final float GROUND_FORCE = 50f;
    private static final float AIR_FORCE = 10f;
    private static final float GROUND_DAMPING = 0.9f;
    private static final float AIR_DAMPING = 0.1f;
    private static final float MAX_SLOPE_ANGLE = 55f;
    private static final float GROUND_RAY_EXTRA = 0.35f;
    private static final float CAPSULE_HALF_HEIGHT = 0.9f;
    private static final float SLOPE_SPEED_PENALTY_START = 10f;
    private static final float SLOPE_STAMINA_DRAIN_SCALE = 2.0f;
    private static final float EXHAUSTED_SPEED_MULTIPLIER = 0.4f;
    private static final float SLOPE_FORCE_BOOST = 2.5f;
    private static final float SLOPE_DAMPING_MIN = 0.4f;

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);

    private final btDiscreteDynamicsWorld dynamicsWorld;
    private final Vector3 planetCenter = new Vector3(0, 0, 0);

    private final Vector3 tempVec = new Vector3();
    private final Vector3 tempVec2 = new Vector3();
    private final Vector3 tempVec3 = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();
    private final Matrix4 tempMat = new Matrix4();
    private final Quaternion tempQuat = new Quaternion();

    public PlayerMovementSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        super(Family.all(
            PlayerInputComponent.class,
            PhysicsBodyComponent.class,
            MovementStateComponent.class,
            TransformComponent.class
        ).get(), 1);
        this.dynamicsWorld = dynamicsWorld;
    }

    public void setPlanetCenter(Vector3 center) {
        planetCenter.set(center);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        SwimmingStateComponent swimState = entity.getComponent(SwimmingStateComponent.class);
        if (swimState != null && swimState.swimState != SwimState.DRY) {
            return;
        }

        PlayerInputComponent input = inputMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);

        localUp.set(tempVec).sub(planetCenter);
        if (localUp.len2() < 0.001f) localUp.set(0, 1, 0);
        else localUp.nor();

        if (cam != null) {
            cam.localUp.set(localUp);
        }

        boolean wasGrounded = state.isGrounded;
        performGroundCheck(physics, state, tempVec);

        if (cam != null) {
            cam.yawAngle -= input.mouseDeltaX * cam.mouseSensitivity;
            cam.pitchAngle += input.mouseDeltaY * cam.mouseSensitivity;
            cam.pitchAngle = MathUtils.clamp(cam.pitchAngle, -85f, 85f);
        }

        buildTangentFrame(cam != null ? -cam.yawAngle : 0f);

        float dirFwd = input.moveForward;
        float dirRight = input.moveStrafe;
        tempVec2.set(localForward).scl(dirFwd).add(tempVec3.set(localRight).scl(dirRight));
        float len = tempVec2.len();
        if (len > 0.001f) tempVec2.scl(1f / len);

        orientCapsule(physics, cam != null ? cam.yawAngle : 0f);

        float slopeAngle = state.slopeAngle;
        boolean movingUphill = false;
        if (state.isGrounded && len > 0.001f && slopeAngle > 1f) {
            float slopeHorizLen = projectOnTangent(state.groundNormal);
            if (slopeHorizLen > 0.001f) {
                float dot = tempVec2.x * tempVec3.x + tempVec2.y * tempVec3.y + tempVec2.z * tempVec3.z;
                movingUphill = dot > 0.1f;
            }
        }

        float slopeSpeedFactor = 1f;
        float slopeStaminaDrain = 0f;
        if (movingUphill && slopeAngle > SLOPE_SPEED_PENALTY_START) {
            float slopeFrac = (slopeAngle - SLOPE_SPEED_PENALTY_START) / (MAX_SLOPE_ANGLE - SLOPE_SPEED_PENALTY_START);
            slopeFrac = Math.min(1f, slopeFrac);
            slopeSpeedFactor = 1f - slopeFrac * 0.6f;
            slopeStaminaDrain = slopeFrac * state.staminaDrainRate * SLOPE_STAMINA_DRAIN_SCALE;
        }

        float forceMult = state.isGrounded ? GROUND_FORCE : AIR_FORCE;

        boolean wantsSprint = input.sprint && state.currentStamina > 0 && !input.crouch;
        state.isSprinting = wantsSprint && state.isGrounded && len > 0.001f;
        state.isCrouching = input.crouch;

        float targetSpeed = WALK_SPEED;
        if (state.isSprinting) targetSpeed = SPRINT_SPEED;
        if (state.isCrouching) targetSpeed = CROUCH_SPEED;

        targetSpeed *= slopeSpeedFactor;
        if (state.isExhausted) targetSpeed *= EXHAUSTED_SPEED_MULTIPLIER;

        Vector3 currentVel = physics.body.getLinearVelocity();
        float upComponent = currentVel.dot(localUp);
        float currentHorizSpeed = (float) Math.sqrt(
            currentVel.len2() - upComponent * upComponent);

        if (len > 0.001f && currentHorizSpeed < targetSpeed) {
            if (state.isGrounded && slopeAngle > 1f) {
                projectOnPlane(tempVec2, state.groundNormal);
                if (tempVec2.len2() > 0.001f) tempVec2.nor();
                float boost = movingUphill ? SLOPE_FORCE_BOOST : 1f;
                tempVec2.scl(forceMult * physics.mass * boost);
            } else {
                tempVec2.scl(forceMult * physics.mass);
            }
            physics.body.applyCentralForce(tempVec2);
        }

        if (state.isGrounded && slopeAngle > 1f) {
            float nDotUp = state.groundNormal.dot(localUp);
            float lateralScale = 1f - nDotUp * nDotUp;
            tempVec3.set(localUp).scl(9.81f * lateralScale * physics.mass);
            physics.body.applyCentralForce(tempVec3);
        }

        float groundDamp = GROUND_DAMPING;
        if (state.isGrounded && slopeAngle > SLOPE_SPEED_PENALTY_START) {
            float slopeFrac = Math.min(1f,
                (slopeAngle - SLOPE_SPEED_PENALTY_START) / (MAX_SLOPE_ANGLE - SLOPE_SPEED_PENALTY_START));
            groundDamp = GROUND_DAMPING - slopeFrac * (GROUND_DAMPING - SLOPE_DAMPING_MIN);
        }
        physics.body.setDamping(
            state.isGrounded ? groundDamp : AIR_DAMPING, 0f);

        if (input.jumpRequested && state.isGrounded) {
            tempVec3.set(localUp).scl(JUMP_IMPULSE * physics.mass);
            physics.body.applyCentralImpulse(tempVec3);
            state.isGrounded = false;
        }
        input.jumpRequested = false;

        float totalStaminaDrain = slopeStaminaDrain;
        if (state.isSprinting) {
            totalStaminaDrain += state.staminaDrainRate;
        }

        if (totalStaminaDrain > 0 && len > 0.001f) {
            state.currentStamina -= totalStaminaDrain * deltaTime;
            if (state.currentStamina <= 0) {
                state.currentStamina = 0;
                state.isSprinting = false;
                state.isExhausted = true;
            }
        } else {
            state.currentStamina = Math.min(state.maxStamina,
                state.currentStamina + state.staminaRegenRate * deltaTime);
            if (state.currentStamina > state.maxStamina * 0.2f) {
                state.isExhausted = false;
            }
        }

        if (!wasGrounded && state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.dot(localUp));
        } else if (!state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.dot(localUp));
        }

        state.currentSpeed = currentHorizSpeed;

        physics.body.activate();
    }

    private void buildTangentFrame(float yawAngle) {
        Vector3 ref = Math.abs(localUp.y) < 0.999f ? Vector3.Y : Vector3.Z;
        localRight.set(localUp).crs(ref).nor();
        localForward.set(localUp).crs(localRight).nor();

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

    private void orientCapsule(PhysicsBodyComponent physics, float yawAngle) {
        tempQuat.setFromCross(Vector3.Y, localUp);
        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);
        tempMat.set(tempVec, tempQuat);
        physics.body.setWorldTransform(tempMat);
    }

    private void performGroundCheck(PhysicsBodyComponent physics, MovementStateComponent state, Vector3 bodyPos) {
        rayFrom.set(bodyPos);
        rayTo.set(localUp).scl(-(CAPSULE_HALF_HEIGHT + GROUND_RAY_EXTRA)).add(bodyPos);

        ClosestRayResultCallback callback = new ClosestRayResultCallback(rayFrom, rayTo);
        callback.setCollisionFilterMask((short) 2);
        dynamicsWorld.rayTest(rayFrom, rayTo, callback);

        if (callback.hasHit()) {
            callback.getHitNormalWorld(tempVec2);
            state.groundNormal.set(tempVec2);
            float angle = (float) Math.toDegrees(Math.acos(
                Math.min(1f, tempVec2.dot(localUp))));
            state.slopeAngle = angle;
            state.isGrounded = angle <= MAX_SLOPE_ANGLE;
        } else {
            state.isGrounded = false;
            state.slopeAngle = 0;
            state.groundNormal.set(localUp);
        }

        callback.dispose();
    }

    private void projectOnPlane(Vector3 vec, Vector3 normal) {
        float dot = vec.dot(normal);
        vec.x -= normal.x * dot;
        vec.y -= normal.y * dot;
        vec.z -= normal.z * dot;
    }

    private float projectOnTangent(Vector3 groundNormal) {
        tempVec3.set(groundNormal);
        float dot = tempVec3.dot(localUp);
        tempVec3.x -= localUp.x * dot;
        tempVec3.y -= localUp.y * dot;
        tempVec3.z -= localUp.z * dot;
        float len = tempVec3.len();
        if (len > 0.001f) tempVec3.scl(1f / len);
        return len;
    }
}
