package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.events.DeckWashEvent;
import com.galacticodyssey.water.events.DeckAwashEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeckWashSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private DeckWashSystem deckWashSystem;
    private Entity shipEntity;
    private HullComponent hull;
    private FloodingComponent flooding;
    private DeckWashComponent deckWash;

    private final List<DeckWashEvent> washEvents = new ArrayList<>();
    private final List<DeckAwashEvent> awashEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        deckWashSystem = new DeckWashSystem(12, eventBus);
        engine = new Engine();
        engine.addSystem(deckWashSystem);

        shipEntity = new Entity();
        hull = new HullComponent();

        BuoyancySamplePoint gunwalePoint = new BuoyancySamplePoint();
        gunwalePoint.localOffset.set(2f, 3f, 0f);
        gunwalePoint.normal.set(0f, 1f, 0f);
        gunwalePoint.area = 1.0f;
        hull.samplePoints.add(gunwalePoint);

        flooding = new FloodingComponent();
        Compartment topComp = new Compartment("upper_deck", 50f);
        flooding.compartments.add(topComp);

        deckWash = new DeckWashComponent();
        deckWash.gunwaleSampleIndices.add(0);
        deckWash.deckHeight = 3f;
        deckWash.topCompartmentId = "upper_deck";
        deckWash.gunwaleSegmentLength = 2.0f;

        PhysicsBodyComponent physics = new PhysicsBodyComponent();

        shipEntity.add(hull);
        shipEntity.add(flooding);
        shipEntity.add(deckWash);
        shipEntity.add(physics);
        engine.addEntity(shipEntity);

        deckWashSystem.setTestWaterSurfaceHeight(Float.NaN);

        eventBus.subscribe(DeckWashEvent.class, washEvents::add);
        eventBus.subscribe(DeckAwashEvent.class, awashEvents::add);
    }

    @Test
    void noWashWhenWavesBelowGunwale() {
        deckWashSystem.setTestWaterSurfaceHeight(2.5f);

        engine.update(1f / 60f);

        assertTrue(washEvents.isEmpty());
        Compartment comp = flooding.compartments.get(0);
        assertEquals(0f, comp.waterVolume, 0.001f);
    }

    @Test
    void waterEntersWhenWavesExceedGunwale() {
        deckWashSystem.setTestWaterSurfaceHeight(4.0f);

        engine.update(1.0f);

        assertFalse(washEvents.isEmpty());
        Compartment comp = flooding.compartments.get(0);
        assertTrue(comp.waterVolume > 0f);
    }

    @Test
    void flowRateIncreasesWithOvertoppingDepth() {
        deckWashSystem.setTestWaterSurfaceHeight(3.5f);
        engine.update(1.0f);
        float lowOvertopping = flooding.compartments.get(0).waterVolume;

        flooding.compartments.get(0).waterVolume = 0f;
        washEvents.clear();

        deckWashSystem.setTestWaterSurfaceHeight(5.0f);
        engine.update(1.0f);
        float highOvertopping = flooding.compartments.get(0).waterVolume;

        assertTrue(highOvertopping > lowOvertopping);
    }
}
