package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.StormApproachingEvent;
import com.galacticodyssey.water.events.StormEnteredEvent;
import com.galacticodyssey.water.events.StormExitedEvent;
import com.galacticodyssey.water.events.StormPhaseChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private WeatherSystem weatherSystem;
    private Entity stormEntity;
    private StormCellComponent storm;
    private WaterBodyComponent waterBody;
    private Entity waterEntity;

    private final List<StormPhaseChangedEvent> phaseEvents = new ArrayList<>();
    private final List<StormApproachingEvent> approachEvents = new ArrayList<>();
    private final List<StormEnteredEvent> enteredEvents = new ArrayList<>();
    private final List<StormExitedEvent> exitedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();

        StormConfigData stormConfig = new StormConfigData();
        stormConfig.edgeBandFraction = 0.3f;
        stormConfig.approachWarningDistance = 500f;
        registry.setStormConfig(stormConfig);

        for (WeatherPhase phase : WeatherPhase.values()) {
            WeatherProfileData profile = new WeatherProfileData();
            profile.phase = phase.name();
            profile.minDuration = 10f;
            profile.maxDuration = 20f;
            profile.waveCount = phase == WeatherPhase.STORM ? 6 : 2;
            profile.minAmplitude = phase == WeatherPhase.STORM ? 3f : 0.3f;
            profile.maxAmplitude = phase == WeatherPhase.STORM ? 6f : 0.8f;
            profile.minSteepness = 0.1f;
            profile.maxSteepness = phase == WeatherPhase.STORM ? 0.8f : 0.15f;
            profile.directionSpread = 15f;
            profile.minWindSpeed = 0f;
            profile.maxWindSpeed = phase == WeatherPhase.STORM ? 30f : 5f;
            profile.lerpRate = 0.5f;
            registry.registerWeatherProfile(profile);
        }

        weatherSystem = new WeatherSystem(5, eventBus, registry);
        engine = new Engine();
        engine.addSystem(weatherSystem);

        stormEntity = new Entity();
        storm = new StormCellComponent();
        storm.currentPhase = WeatherPhase.CALM;
        storm.phaseDuration = 10f;
        storm.phaseTimer = 0f;
        storm.radius = 3000f;
        storm.centerGalaxyX = 0;
        storm.centerGalaxyZ = 0;
        stormEntity.add(storm);
        engine.addEntity(stormEntity);

        waterEntity = new Entity();
        waterBody = new WaterBodyComponent();
        waterEntity.add(waterBody);
        engine.addEntity(waterEntity);

        weatherSystem.setActiveWaterBody(waterBody);

        eventBus.subscribe(StormPhaseChangedEvent.class, phaseEvents::add);
        eventBus.subscribe(StormApproachingEvent.class, approachEvents::add);
        eventBus.subscribe(StormEnteredEvent.class, enteredEvents::add);
        eventBus.subscribe(StormExitedEvent.class, exitedEvents::add);
    }

    @Test
    void stormStartsInCalmPhase() {
        assertEquals(WeatherPhase.CALM, storm.currentPhase);
    }

    @Test
    void phaseTransitionsAfterDurationExpires() {
        storm.phaseDuration = 1.0f;
        storm.phaseTimer = 0f;

        for (int i = 0; i < 70; i++) {
            engine.update(1f / 60f);
        }

        assertEquals(WeatherPhase.BUILDING, storm.currentPhase);
        assertEquals(1, phaseEvents.size());
        assertEquals(WeatherPhase.CALM, phaseEvents.get(0).oldPhase);
        assertEquals(WeatherPhase.BUILDING, phaseEvents.get(0).newPhase);
    }

    @Test
    void fullStormCycleReturnsToCalm() {
        storm.phaseDuration = 0.1f;

        for (int i = 0; i < 4; i++) {
            storm.phaseTimer = storm.phaseDuration + 0.01f;
            engine.update(1f / 60f);
        }

        assertEquals(WeatherPhase.CALM, storm.currentPhase);
        assertEquals(4, phaseEvents.size());
    }

    @Test
    void phaseTimerIncrementsEachTick() {
        float before = storm.phaseTimer;
        engine.update(0.5f);
        assertTrue(storm.phaseTimer > before);
    }

    @Test
    void stormCellDriftsWithWind() {
        storm.driftVelocityX = 10f;
        storm.driftVelocityZ = 5f;

        double startX = storm.centerGalaxyX;
        double startZ = storm.centerGalaxyZ;

        engine.update(1.0f);

        assertEquals(startX + 10.0, storm.centerGalaxyX, 0.1);
        assertEquals(startZ + 5.0, storm.centerGalaxyZ, 0.1);
    }

    @Test
    void publishesStormEnteredWhenPlayerMovesInside() {
        weatherSystem.setPlayerGalaxyPosition(0, 0);
        storm.centerGalaxyX = 0;
        storm.centerGalaxyZ = 0;
        storm.radius = 3000f;
        storm.playerInside = false;

        engine.update(1f / 60f);

        assertTrue(storm.playerInside);
        assertEquals(1, enteredEvents.size());
    }

    @Test
    void publishesStormExitedWhenPlayerLeavesRadius() {
        storm.playerInside = true;
        weatherSystem.setPlayerGalaxyPosition(10000, 10000);

        engine.update(1f / 60f);

        assertFalse(storm.playerInside);
        assertEquals(1, exitedEvents.size());
    }

    @Test
    void publishesStormApproachingAtWarningDistance() {
        storm.centerGalaxyX = 3400;
        storm.centerGalaxyZ = 0;
        storm.radius = 3000f;
        storm.playerInside = false;
        storm.playerApproaching = false;
        weatherSystem.setPlayerGalaxyPosition(0, 0);

        engine.update(1f / 60f);

        assertTrue(storm.playerApproaching);
        assertEquals(1, approachEvents.size());
    }
}
