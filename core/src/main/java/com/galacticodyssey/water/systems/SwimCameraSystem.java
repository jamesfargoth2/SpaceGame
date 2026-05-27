package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;

public class SwimCameraSystem extends IteratingSystem {

    private static final float SURFACE_Y_LERP_SPEED = 5.0f;
    private static final float ROLL_FACTOR = 0.3f;
    private static final float UNDERWATER_SWAY_AMPLITUDE = 0.5f;
    private static final float UNDERWATER_SWAY_FREQ = 0.3f;

    private final ComponentMapper<SwimmingStateComponent> swimMapper =
        ComponentMapper.getFor(SwimmingStateComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final SwimConfigData config;
    private WaveSystem waveSystem;
    private float time;

    public SwimCameraSystem(int priority, WaterDataRegistry registry) {
        super(Family.all(
            SwimmingStateComponent.class,
            FPSCameraComponent.class,
            TransformComponent.class
        ).get(), priority);
        this.config = registry.getSwimConfig();
    }

    public void setWaveSystem(WaveSystem waveSystem) {
        this.waveSystem = waveSystem;
    }

    @Override
    public void update(float dt) {
        time += dt;
        super.update(dt);
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        SwimmingStateComponent swim = swimMapper.get(entity);
        FPSCameraComponent camera = cameraMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        if (swim.swimState == SwimState.DRY) return;

        switch (swim.swimState) {
            case WADING:
                camera.headBobAmplitude = 0.02f;
                camera.headBobFrequency = 6.0f;
                break;

            case SURFACE:
                float targetY = swim.waterSurfaceHeight + config.surfaceEyeOffset;
                camera.currentEyeHeight = MathUtils.lerp(
                    camera.currentEyeHeight, targetY - transform.position.y,
                    SURFACE_Y_LERP_SPEED * dt);

                if (waveSystem != null) {
                    Vector3 normal = Pools.obtain(Vector3.class);
                    // WaveSystem.getNormal uses galaxy-space doubles; cast local floats
                    waveSystem.getNormal(
                        (double) transform.position.x,
                        (double) transform.position.z,
                        normal);
                    @SuppressWarnings("unused")
                    float rollTarget = (float) Math.atan2(normal.x, normal.y) * ROLL_FACTOR;
                    Pools.free(normal);
                }

                camera.headBobAmplitude = 0f;
                break;

            case DIVING:
            case SUBMERGED:
                @SuppressWarnings("unused")
                float sway = MathUtils.sin(time * MathUtils.PI2 * UNDERWATER_SWAY_FREQ)
                    * UNDERWATER_SWAY_AMPLITUDE;
                camera.headBobAmplitude = 0f;
                break;

            case DROWNING:
                camera.headBobAmplitude = 0f;
                break;

            default:
                break;
        }

        if (swim.hasAscentSickness && swim.ascentSicknessTimer > 0f) {
            swim.ascentSicknessTimer -= dt;
            if (swim.ascentSicknessTimer <= 0f) {
                swim.hasAscentSickness = false;
                swim.ascentSicknessTimer = 0f;
            }
        }
    }
}
