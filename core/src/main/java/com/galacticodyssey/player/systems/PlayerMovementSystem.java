package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

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
    private static final float GRAVITY = 9.81f;

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

    private final Vector3 tempVec = new Vector3();
    private final Vector3 tempVec2 = new Vector3();
    private final Vector3 tempVec3 = new Vector3();
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public PlayerMovementSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        super(Family.all(
            PlayerInputComponent.class,
            PhysicsBodyComponent.class,
            MovementStateComponent.class,
            TransformComponent.class
        ).get(), 1);
        this.dynamicsWorld = dynamicsWorld;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
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

        boolean wasGrounded = state.isGrounded;
        performGroundCheck(physics, state, tempVec);

        if (cam != null) {
            cam.yawAngle += input.mouseDeltaX * cam.mouseSensitivity;
            cam.pitchAngle += input.mouseDeltaY * cam.mouseSensitivity;
            cam.pitchAngle = MathUtils.clamp(cam.pitchAngle, -85f, 85f);

            tempMat.setToRotation(Vector3.Y, cam.yawAngle);
            tempMat.setTranslation(tempVec);
            physics.body.setWorldTransform(tempMat);
        }

        float yawRad = cam != null ? cam.yawAngle * MathUtils.degreesToRadians : 0;
        float forwardX = -MathUtils.sin(yawRad);
        float forwardZ = -MathUtils.cos(yawRad);
        float rightX = MathUtils.cos(yawRad);
        float rightZ = -MathUtils.sin(yawRad);

        float dirX = forwardX * input.moveForward + rightX * input.moveStrafe;
        float dirZ = forwardZ * input.moveForward + rightZ * input.moveStrafe;
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.001f) {
            dirX /= len;
            dirZ /= len;
        }

        float slopeAngle = state.slopeAngle;
        boolean movingUphill = false;
        if (state.isGrounded && len > 0.001f && slopeAngle > 1f) {
            float slopeHorizX = state.groundNormal.x;
            float slopeHorizZ = state.groundNormal.z;
            float slopeHorizLen = (float) Math.sqrt(slopeHorizX * slopeHorizX + slopeHorizZ * slopeHorizZ);
            if (slopeHorizLen > 0.001f) {
                float uphillDirX = slopeHorizX / slopeHorizLen;
                float uphillDirZ = slopeHorizZ / slopeHorizLen;
                float dot = dirX * uphillDirX + dirZ * uphillDirZ;
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
        float currentHorizSpeed = (float) Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

        if (len > 0.001f && currentHorizSpeed < targetSpeed) {
            if (state.isGrounded && slopeAngle > 1f) {
                tempVec2.set(dirX, 0, dirZ);
                projectOnPlane(tempVec2, state.groundNormal);
                if (tempVec2.len2() > 0.001f) {
                    tempVec2.nor();
                }
                float boost = movingUphill ? SLOPE_FORCE_BOOST : 1f;
                tempVec2.scl(forceMult * physics.mass * boost);
            } else {
                tempVec2.set(dirX * forceMult * physics.mass, 0, dirZ * forceMult * physics.mass);
            }
            physics.body.applyCentralForce(tempVec2);
        }

        if (state.isGrounded && slopeAngle > 1f) {
            float nx = state.groundNormal.x;
            float ny = state.groundNormal.y;
            float nz = state.groundNormal.z;
            tempVec3.set(
                -GRAVITY * ny * nx * physics.mass,
                GRAVITY * (nx * nx + nz * nz) * physics.mass,
                -GRAVITY * ny * nz * physics.mass
            );
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
            tempVec3.set(0, JUMP_IMPULSE * physics.mass, 0);
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
            state.fallVelocity = Math.abs(currentVel.y);
        } else if (!state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.y);
        }

        state.currentSpeed = currentHorizSpeed;

        physics.body.activate();
    }

    private void performGroundCheck(PhysicsBodyComponent physics, MovementStateComponent state, Vector3 bodyPos) {
        rayFrom.set(bodyPos.x, bodyPos.y, bodyPos.z);
        rayTo.set(bodyPos.x, bodyPos.y - CAPSULE_HALF_HEIGHT - GROUND_RAY_EXTRA, bodyPos.z);

        ClosestRayResultCallback callback = new ClosestRayResultCallback(rayFrom, rayTo);
        dynamicsWorld.rayTest(rayFrom, rayTo, callback);

        if (callback.hasHit()) {
            callback.getHitNormalWorld(tempVec2);
            state.groundNormal.set(tempVec2);
            float angle = (float) Math.toDegrees(Math.acos(
                Math.min(1f, tempVec2.dot(Vector3.Y))));
            state.slopeAngle = angle;
            state.isGrounded = angle <= MAX_SLOPE_ANGLE;
        } else {
            state.isGrounded = false;
            state.slopeAngle = 0;
            state.groundNormal.set(0, 1, 0);
        }

        callback.dispose();
    }

    private void projectOnPlane(Vector3 vec, Vector3 normal) {
        float dot = vec.dot(normal);
        vec.x -= normal.x * dot;
        vec.y -= normal.y * dot;
        vec.z -= normal.z * dot;
    }
}
