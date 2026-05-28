package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.events.*;

public class SwimmingSystem extends IteratingSystem {

    private static final float CAPSULE_HALF_HEIGHT = 0.9f;

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<MovementStateComponent> moveMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<FPSCameraComponent> camMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);

    private final EventBus eventBus;
    private final SwimConfigData config;

    private WaveSystem waveSystem;
    private float testWaterSurfaceHeight = Float.NaN;

    public SwimmingSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            PlayerInputComponent.class,
            MovementStateComponent.class,
            TransformComponent.class,
            PhysicsBodyComponent.class
        ).get(), priority);
        this.eventBus = eventBus;
        this.config = registry.getSwimConfig();
    }

    public void setWaveSystem(WaveSystem waveSystem) {
        this.waveSystem = waveSystem;
    }

    public void setTestWaterSurfaceHeight(float height) {
        this.testWaterSurfaceHeight = height;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        PlayerInputComponent input = inputMapper.get(entity);
        MovementStateComponent movement = moveMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        float waterSurface = getWaterSurfaceHeight(transform.position, swim);
        swim.waterSurfaceHeight = waterSurface;

        float footY = transform.position.y - CAPSULE_HALF_HEIGHT;
        float waistY = transform.position.y;
        float chestY = transform.position.y + CAPSULE_HALF_HEIGHT * 0.3f;
        float headY = transform.position.y + CAPSULE_HALF_HEIGHT;

        float immersion = 0f;
        if (waterSurface > footY) {
            immersion = MathUtils.clamp(
                (waterSurface - footY) / (headY - footY), 0f, 1f);
        }
        swim.immersionFraction = immersion;

        swim.previousDepth = swim.currentDepth;
        swim.currentDepth = Math.max(0f, waterSurface - transform.position.y);
        swim.verticalSpeed = (swim.previousDepth - swim.currentDepth) / Math.max(dt, 0.001f);

        swim.previousState = swim.swimState;

        updateBreath(swim, input, dt);

        updateStateMachine(entity, swim, input, movement, transform, waterSurface,
            footY, chestY, headY, dt);
        updateStamina(swim, movement, input, dt);
        applySwimPhysics(entity, swim, input, movement, transform, physics, waterSurface, dt);
    }

    private void updateStateMachine(Entity entity, SwimmingStateComponent swim,
            PlayerInputComponent input, MovementStateComponent movement,
            TransformComponent transform, float waterSurface,
            float footY, float chestY, float headY, float dt) {

        switch (swim.swimState) {
            case DRY:
                if (waterSurface > footY + config.wadeDepthFoot) {
                    swim.swimState = SwimState.WADING;
                    eventBus.publish(new PlayerEnteredWaterEvent(entity, null));
                }
                break;

            case WADING:
                if (waterSurface < transform.position.y) {
                    swim.swimState = SwimState.DRY;
                    eventBus.publish(new PlayerExitedWaterEvent(entity));
                } else if (waterSurface > chestY || !movement.isGrounded) {
                    swim.swimState = SwimState.SURFACE;
                    swim.breath = swim.maxBreath;
                }
                break;

            case SURFACE:
                if (movement.isGrounded && waterSurface < chestY) {
                    swim.swimState = SwimState.WADING;
                } else if (input.crouch) {
                    swim.swimState = SwimState.DIVING;
                    eventBus.publish(new PlayerStartedDivingEvent(entity, swim.currentDepth));
                }
                if (swim.swimState == SwimState.SURFACE) {
                    swim.breath = Math.min(swim.maxBreath,
                        swim.breath + config.breathRefillRate * dt);
                }
                break;

            case DIVING:
                if (swim.currentDepth >= config.diveToSubmergedDepth) {
                    swim.swimState = SwimState.SUBMERGED;
                } else if (!input.crouch && swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                } else if (swim.breath <= 0f) {
                    swim.breath = 0f;
                    swim.swimState = SwimState.DROWNING;
                    eventBus.publish(new BreathDepletedEvent(entity));
                    eventBus.publish(new PlayerDrowningEvent(entity));
                }
                break;

            case SUBMERGED:
                if (swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                } else if (swim.currentDepth < config.submergedToDiveDepth) {
                    swim.swimState = SwimState.DIVING;
                } else if (swim.breath <= 0f) {
                    swim.breath = 0f;
                    swim.swimState = SwimState.DROWNING;
                    eventBus.publish(new BreathDepletedEvent(entity));
                    eventBus.publish(new PlayerDrowningEvent(entity));
                }
                break;

            case DROWNING:
                if (swim.currentDepth < 0.5f) {
                    swim.swimState = SwimState.SURFACE;
                    eventBus.publish(new PlayerSurfacedEvent(entity));
                }
                break;
        }
    }

    private void updateBreath(SwimmingStateComponent swim, PlayerInputComponent input, float dt) {
        if (swim.swimState == SwimState.DIVING || swim.swimState == SwimState.SUBMERGED) {
            float drain = input.sprint ? config.sprintBreathDrainRate : config.breathDrainRate;
            swim.breath = Math.max(0f, swim.breath - drain * dt);
        }
    }

    private void updateStamina(SwimmingStateComponent swim, MovementStateComponent movement,
                                PlayerInputComponent input, float dt) {
        switch (swim.swimState) {
            case WADING:
                movement.currentStamina -= config.wadingStaminaDrain * dt;
                break;
            case SURFACE:
                float surfaceDrain = input.sprint ?
                    config.sprintSwimStaminaDrain : config.surfaceStaminaDrain;
                movement.currentStamina -= surfaceDrain * dt;
                break;
            case DIVING:
            case SUBMERGED:
                float diveDrain = input.sprint ?
                    config.sprintSwimStaminaDrain : config.surfaceStaminaDrain;
                movement.currentStamina -= diveDrain * dt;
                break;
            default:
                break;
        }
        movement.currentStamina = MathUtils.clamp(
            movement.currentStamina, 0f, movement.maxStamina);
        movement.isExhausted = movement.currentStamina <= 0f;
    }

    private static final float GRAVITY = 9.81f;

    private void applySwimPhysics(Entity entity, SwimmingStateComponent swim,
            PlayerInputComponent input, MovementStateComponent movement,
            TransformComponent transform, PhysicsBodyComponent physics,
            float waterSurface, float dt) {
        if (physics.body == null) return;
        if (swim.swimState == SwimState.DRY) return;

        Vector3 vel = Pools.obtain(Vector3.class);
        vel.set(physics.body.getLinearVelocity());

        // Passive buoyancy: counteract gravity proportional to how submerged the player is.
        // At immersionFraction=1 (fully submerged) this exactly cancels gravity → neutral buoyancy.
        // At immersionFraction=0.5 it partially reduces effective weight while wading.
        if (swim.swimState != SwimState.DROWNING) {
            float buoyancy = GRAVITY * swim.immersionFraction;
            Vector3 buoyancyForce = Pools.obtain(Vector3.class);
            buoyancyForce.set(0f, buoyancy * physics.mass, 0f);
            physics.body.applyCentralForce(buoyancyForce);
            Pools.free(buoyancyForce);
        }

        // Surface position spring: a gentle corrector that settles the player at eye level.
        // Passive buoyancy already handles the bulk of gravity, so the spring only needs to
        // correct the small residual positional error — keeping buoyancySpringK low (10)
        // ensures the system is overdamped (no bounce) with the current buoyancyDamping (8).
        if (swim.swimState == SwimState.SURFACE) {
            float targetY = waterSurface + config.surfaceEyeOffset - CAPSULE_HALF_HEIGHT;
            float errorY = targetY - transform.position.y;
            float springForce = config.buoyancySpringK * errorY - config.buoyancyDamping * vel.y;

            Vector3 force = Pools.obtain(Vector3.class);
            force.set(0f, springForce * physics.mass, 0f);
            physics.body.applyCentralForce(force);
            Pools.free(force);
        }

        if (swim.swimState == SwimState.DROWNING) {
            Vector3 floatForce = Pools.obtain(Vector3.class);
            floatForce.set(0f, config.drowningFloatSpeed * physics.mass, 0f);
            physics.body.applyCentralForce(floatForce);
            Pools.free(floatForce);
        }

        if (swim.swimState != SwimState.DROWNING) {
            float speed = vel.len();
            if (speed > 0.01f) {
                float dragMag = 0.5f * 1025f * config.playerDragCoefficient
                    * config.playerCrossSectionArea * speed * speed;
                Vector3 drag = Pools.obtain(Vector3.class);
                drag.set(vel).nor().scl(-dragMag);
                physics.body.applyCentralForce(drag);
                Pools.free(drag);
            }
        }

        if (swim.swimState == SwimState.WADING || swim.swimState == SwimState.SURFACE) {
            applyHorizontalSwimForce(entity, input, movement, physics, vel);
        } else if (swim.swimState == SwimState.DIVING || swim.swimState == SwimState.SUBMERGED) {
            apply3DSwimForce(entity, input, movement, physics, vel);
        }

        Pools.free(vel);
    }

    private void applyHorizontalSwimForce(Entity entity, PlayerInputComponent input,
            MovementStateComponent movement, PhysicsBodyComponent physics, Vector3 vel) {
        if (input.moveForward == 0 && input.moveStrafe == 0) return;
        FPSCameraComponent cam = camMapper.get(entity);
        if (cam == null) return;

        float yawRad = -cam.yawAngle * MathUtils.degreesToRadians;
        float cosYaw = MathUtils.cos(yawRad);
        float sinYaw = MathUtils.sin(yawRad);

        float fx = sinYaw * input.moveForward + cosYaw * input.moveStrafe;
        float fz = -cosYaw * input.moveForward + sinYaw * input.moveStrafe;
        float len = (float) Math.sqrt(fx * fx + fz * fz);
        if (len < 0.001f) return;

        float targetSpeed = config.surfaceSwimSpeed;
        if (input.sprint && !movement.isExhausted) targetSpeed *= config.sprintSwimMultiplier;

        float horizSpeedSq = vel.x * vel.x + vel.z * vel.z;
        if (horizSpeedSq < targetSpeed * targetSpeed) {
            Vector3 swimForce = Pools.obtain(Vector3.class);
            swimForce.set(fx / len * config.surfaceSwimForce * physics.mass,
                          0f,
                          fz / len * config.surfaceSwimForce * physics.mass);
            physics.body.applyCentralForce(swimForce);
            Pools.free(swimForce);
        }
    }

    private void apply3DSwimForce(Entity entity, PlayerInputComponent input,
            MovementStateComponent movement, PhysicsBodyComponent physics, Vector3 vel) {
        if (input.moveForward == 0 && input.moveStrafe == 0) return;
        FPSCameraComponent cam = camMapper.get(entity);
        if (cam == null) return;

        float yawRad = -cam.yawAngle * MathUtils.degreesToRadians;
        float pitchRad = cam.pitchAngle * MathUtils.degreesToRadians;
        float cosYaw = MathUtils.cos(yawRad);
        float sinYaw = MathUtils.sin(yawRad);
        float cosPitch = MathUtils.cos(pitchRad);
        float sinPitch = MathUtils.sin(pitchRad);

        float fx = sinYaw * cosPitch * input.moveForward + cosYaw * input.moveStrafe;
        float fy = -sinPitch * input.moveForward;
        float fz = -cosYaw * cosPitch * input.moveForward + sinYaw * input.moveStrafe;

        float len = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (len < 0.001f) return;

        float targetSpeed = config.diveSwimSpeed;
        if (input.sprint && !movement.isExhausted) targetSpeed *= config.sprintSwimMultiplier;

        if (vel.len2() < targetSpeed * targetSpeed) {
            Vector3 swimForce = Pools.obtain(Vector3.class);
            swimForce.set(fx / len * config.diveSwimForce * physics.mass,
                          fy / len * config.diveSwimForce * physics.mass,
                          fz / len * config.diveSwimForce * physics.mass);
            physics.body.applyCentralForce(swimForce);
            Pools.free(swimForce);
        }
    }

    private float getWaterSurfaceHeight(Vector3 localPos, SwimmingStateComponent swim) {
        if (!Float.isNaN(testWaterSurfaceHeight)) {
            return testWaterSurfaceHeight;
        }
        if (swim.isInInteriorWater) {
            return swim.interiorWaterLevel;
        }
        if (waveSystem != null) {
            return waveSystem.getHeight(localPos.x, localPos.z);
        }
        return 0f;
    }
}
