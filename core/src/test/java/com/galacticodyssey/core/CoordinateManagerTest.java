package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateManagerTest {

    private EventBus eventBus;
    private CoordinateManager coordManager;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        coordManager = new CoordinateManager(eventBus);
    }

    @Test
    void toLocalSpaceAtOriginReturnsZero() {
        Vector3 local = coordManager.toLocalSpace(0, 0, 0);
        assertEquals(0f, local.x, 0.001f);
        assertEquals(0f, local.y, 0.001f);
        assertEquals(0f, local.z, 0.001f);
    }

    @Test
    void toLocalSpaceReturnsOffsetFromOrigin() {
        Vector3 local = coordManager.toLocalSpace(100.0, 200.0, 300.0);
        assertEquals(100f, local.x, 0.001f);
        assertEquals(200f, local.y, 0.001f);
        assertEquals(300f, local.z, 0.001f);
    }

    @Test
    void toGalaxySpaceInvertsToLocalSpace() {
        Vector3 local = new Vector3(50f, 60f, 70f);
        double[] galaxy = coordManager.toGalaxySpace(local);
        assertEquals(50.0, galaxy[0], 0.001);
        assertEquals(60.0, galaxy[1], 0.001);
        assertEquals(70.0, galaxy[2], 0.001);
    }

    @Test
    void rebasePreservesDoublePrecision() {
        double farX = 1_000_000_000.123;
        double farY = 0;
        double farZ = 0;

        Vector3 local = coordManager.toLocalSpace(farX, farY, farZ);
        coordManager.checkRebase(local);

        double[] galaxy = coordManager.toGalaxySpace(new Vector3(0, 0, 0));
        assertEquals(farX, galaxy[0], 0.01);
    }

    @Test
    void checkRebaseBelowThresholdDoesNotFire() {
        List<OriginRebasedEvent> events = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, events::add);

        coordManager.checkRebase(new Vector3(500f, 0f, 0f));

        assertTrue(events.isEmpty());
    }

    @Test
    void checkRebaseAboveThresholdFiresEvent() {
        List<OriginRebasedEvent> events = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, events::add);

        coordManager.checkRebase(new Vector3(1500f, 0f, 200f));

        assertEquals(1, events.size());
        assertEquals(1500f, events.get(0).deltaX, 0.001f);
        assertEquals(0f, events.get(0).deltaY, 0.001f);
        assertEquals(200f, events.get(0).deltaZ, 0.001f);
    }

    @Test
    void afterRebaseLocalSpaceShifts() {
        coordManager.checkRebase(new Vector3(2000f, 0f, 0f));

        Vector3 local = coordManager.toLocalSpace(2000.0, 0, 0);
        assertEquals(0f, local.x, 0.01f);
    }
}
