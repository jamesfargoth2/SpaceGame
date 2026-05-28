package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.SOITrackerComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.SOIChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SOITrackingSystemTest {

    private Engine engine;
    private SOITrackingSystem soiSystem;
    private EventBus eventBus;
    private Entity starEntity;
    private Entity planetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        soiSystem = new SOITrackingSystem(eventBus);
        engine.addSystem(soiSystem);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starTransform.position.set(0f, 0f, 0f);
        starEntity.add(starTransform);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        planetEntity = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planetTransform.position.set(1000f, 0f, 0f);
        planetEntity.add(planetTransform);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.soiRadius = 200f;
        planetOrbital.parentBody = starEntity;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);
    }

    @Test
    void entityInsidePlanetSOIGetsPlanetAsDominant() {
        Entity ship = createShip(1050f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(planetEntity, tracker.dominantBody);
        assertSame(starEntity, tracker.secondaryBody);
    }

    @Test
    void entityOutsideAllPlanetSOIsGetsStarAsDominant() {
        Entity ship = createShip(500f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(starEntity, tracker.dominantBody);
        assertNull(tracker.secondaryBody);
    }

    @Test
    void soiChangePublishesEvent() {
        Entity ship = createShip(500f, 0f, 0f);
        engine.addEntity(ship);
        engine.update(0f);

        List<SOIChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(SOIChangedEvent.class, events::add);

        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        shipTransform.position.set(1050f, 0f, 0f);
        engine.update(0f);

        assertEquals(1, events.size());
        assertSame(starEntity, events.get(0).oldDominantBody);
        assertSame(planetEntity, events.get(0).newDominantBody);
    }

    @Test
    void distanceToDominantUpdated() {
        Entity ship = createShip(1100f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertEquals(100f, tracker.distanceToDominant, 1f);
    }

    private Entity createShip(float x, float y, float z) {
        Entity ship = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        ship.add(transform);
        ship.add(new SOITrackerComponent());
        return ship;
    }
}
