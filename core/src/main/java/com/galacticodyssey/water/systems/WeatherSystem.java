package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.StormApproachingEvent;
import com.galacticodyssey.water.events.StormEnteredEvent;
import com.galacticodyssey.water.events.StormExitedEvent;
import com.galacticodyssey.water.events.StormPhaseChangedEvent;

public class WeatherSystem extends IteratingSystem {

    private final ComponentMapper<StormCellComponent> stormMapper =
        ComponentMapper.getFor(StormCellComponent.class);

    private final EventBus eventBus;
    private final WaterDataRegistry registry;
    private final StormConfigData stormConfig;
    private WaterBodyComponent activeWaterBody;

    private double playerGalaxyX;
    private double playerGalaxyZ;

    public WeatherSystem(int priority, EventBus eventBus, WaterDataRegistry registry) {
        super(Family.all(StormCellComponent.class).get(), priority);
        this.eventBus = eventBus;
        this.registry = registry;
        this.stormConfig = registry.getStormConfig();
    }

    public void setActiveWaterBody(WaterBodyComponent waterBody) {
        this.activeWaterBody = waterBody;
    }

    public void setPlayerGalaxyPosition(double gx, double gz) {
        this.playerGalaxyX = gx;
        this.playerGalaxyZ = gz;
    }

    @Override
    protected void processEntity(Entity entity, float dt) {
        StormCellComponent storm = stormMapper.get(entity);

        storm.phaseTimer += dt;

        storm.centerGalaxyX += storm.driftVelocityX * dt;
        storm.centerGalaxyZ += storm.driftVelocityZ * dt;

        if (storm.phaseTimer >= storm.phaseDuration) {
            advancePhase(entity, storm);
        }

        checkPlayerProximity(entity, storm);

        if (activeWaterBody != null) {
            lerpWaveParams(storm, dt);
        }
    }

    private void checkPlayerProximity(Entity entity, StormCellComponent storm) {
        double dx = playerGalaxyX - storm.centerGalaxyX;
        double dz = playerGalaxyZ - storm.centerGalaxyZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        boolean wasInside = storm.playerInside;
        boolean wasApproaching = storm.playerApproaching;

        storm.playerInside = distance < storm.radius;

        if (storm.playerInside && !wasInside) {
            eventBus.publish(new StormEnteredEvent(entity, storm.intensity));
        } else if (!storm.playerInside && wasInside) {
            eventBus.publish(new StormExitedEvent(entity));
        }

        float approachDist = storm.radius + stormConfig.approachWarningDistance;
        storm.playerApproaching = !storm.playerInside && distance < approachDist;

        if (storm.playerApproaching && !wasApproaching) {
            float bearing = (float) Math.toDegrees(Math.atan2(dz, dx));
            eventBus.publish(new StormApproachingEvent(entity, distance, bearing));
        }
    }

    private void advancePhase(Entity entity, StormCellComponent storm) {
        WeatherPhase oldPhase = storm.currentPhase;
        WeatherPhase newPhase;

        switch (storm.currentPhase) {
            case CALM:     newPhase = WeatherPhase.BUILDING; break;
            case BUILDING: newPhase = WeatherPhase.STORM;    break;
            case STORM:    newPhase = WeatherPhase.SUBSIDING; break;
            default:       newPhase = WeatherPhase.CALM;      break;
        }

        storm.currentPhase = newPhase;
        storm.phaseTimer = 0f;

        WeatherProfileData profile = registry.getWeatherProfile(newPhase.name());
        if (profile != null) {
            storm.phaseDuration = MathUtils.random(profile.minDuration, profile.maxDuration);
            storm.windSpeed = MathUtils.random(profile.minWindSpeed, profile.maxWindSpeed);
        }

        eventBus.publish(new StormPhaseChangedEvent(entity, oldPhase, newPhase));
    }

    private void lerpWaveParams(StormCellComponent storm, float dt) {
        WeatherProfileData profile = registry.getWeatherProfile(storm.currentPhase.name());
        if (profile == null) return;

        float rate = profile.lerpRate * dt;

        int currentCount = activeWaterBody.waves.size;
        int targetCount = profile.waveCount;

        while (currentCount < targetCount) {
            WaveParams wp = new WaveParams();
            wp.amplitude = 0f;
            wp.wavelength = MathUtils.random(20f, 80f);
            wp.speed = (float) Math.sqrt(9.81f * wp.wavelength / (2f * MathUtils.PI));
            wp.steepness = 0f;
            wp.directionDeg = storm.windDirection + MathUtils.random(
                -profile.directionSpread, profile.directionSpread);
            activeWaterBody.waves.add(wp);
            currentCount++;
        }

        float targetAmp = MathUtils.random(profile.minAmplitude, profile.maxAmplitude)
            * storm.intensity;
        float targetSteepness = MathUtils.random(profile.minSteepness, profile.maxSteepness);

        for (int i = 0; i < activeWaterBody.waves.size; i++) {
            WaveParams wp = activeWaterBody.waves.get(i);
            if (i < targetCount) {
                wp.amplitude = MathUtils.lerp(wp.amplitude, targetAmp, rate);
                wp.steepness = MathUtils.lerp(wp.steepness, targetSteepness, rate);
            } else {
                wp.amplitude = MathUtils.lerp(wp.amplitude, 0f, rate);
                if (wp.amplitude < 0.01f) {
                    activeWaterBody.waves.removeIndex(i);
                    i--;
                }
            }
        }

        float windDirRad = storm.windDirection * MathUtils.degreesToRadians;
        float currentFactor = registry.getSwimConfig() != null ?
            registry.getSwimConfig().windCurrentFactor : 0.03f;
        activeWaterBody.currentVelocity.set(
            MathUtils.cos(windDirRad) * storm.windSpeed * currentFactor,
            0f,
            MathUtils.sin(windDirRad) * storm.windSpeed * currentFactor
        );
    }
}
