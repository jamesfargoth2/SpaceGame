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

public class PlayerMovementSystem extends IteratingSystem {

    private static final float WALK_SPEED = 3.5f;
    private static final float SPRINT_SPEED = 6.0f;
    private static final float CROUCH_SPEED = 1.5f;
    private static final float JUMP_IMPULSE = 5.0f;
    private static final float GROUND_FORCE = 50f;
    private static final float AIR_FORCE = 10f;
    private static final float GROUND_DAMPING = 0.9f;
    private static final float AIR_DAMPING = 0.1f;
    private static final float MAX_SLOPE_ANGLE = 45f;
    private static final float GROUND_RAY_EXTRA = 0.15f;
    private static final float CAPSULE_HALF_HEIGHT = 0.9f;

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

        float forceMult = state.isGrounded ? GROUND_FORCE : AIR_FORCE;

        boolean wantsSprint = input.sprint && state.currentStamina > 0 && !input.crouch;
        state.isSprinting = wantsSprint && state.isGrounded && len > 0.001f;
        state.isCrouching = input.crouch;

        float targetSpeed = WALK_SPEED;
        if (state.isSprinting) targetSpeed = SPRINT_SPEED;
        if (state.isCrouching) targetSpeed = CROUCH_SPEED;

        Vector3 currentVel = physics.body.getLinearVelocity();
        float currentHorizSpeed = (float) Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

        if (len > 0.001f && currentHorizSpeed < targetSpeed) {
            tempVec2.set(dirX * forceMult * physics.mass, 0, dirZ * forceMult * physics.mass);
            physics.body.applyCentralForce(tempVec2);
        }

        physics.body.setDamping(
            state.isGrounded ? GROUND_DAMPING : AIR_DAMPING, 0f);

        if (input.jumpRequested && state.isGrounded) {
            tempVec3.set(0, JUMP_IMPULSE * physics.mass, 0);
            physics.body.applyCentralImpulse(tempVec3);
            state.isGrounded = false;
        }
        input.jumpRequested = false;

        if (state.isSprinting) {
            state.currentStamina -= state.staminaDrainRate * deltaTime;
            if (state.currentStamina <= 0) {
                state.currentStamina = 0;
                state.isSprinting = false;
            }
        } else {
            state.currentStamina = Math.min(state.maxStamina,
                state.currentStamina + state.staminaRegenRate * deltaTime);
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
            state.isGrounded = angle <= MAX_SLOPE_ANGLE;
        } else {
            state.isGrounded = false;
            state.groundNormal.set(0, 1, 0);
        }

        callback.dispose();
    }
}
