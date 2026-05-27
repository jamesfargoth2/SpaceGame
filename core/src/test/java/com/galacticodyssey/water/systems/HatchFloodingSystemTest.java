package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HatchFloodingSystemTest {

    private Engine engine;
    private Entity shipEntity;
    private FloodingComponent flooding;
    private HatchComponent hatches;

    @BeforeEach
    void setUp() {
        EventBus eventBus = new EventBus();
        HatchFloodingSystem system = new HatchFloodingSystem(13, eventBus);
        engine = new Engine();
        engine.addSystem(system);

        shipEntity = new Entity();
        flooding = new FloodingComponent();

        Compartment compA = new Compartment("hold_fore", 100f);
        compA.waterVolume = 50f;
        Compartment compB = new Compartment("hold_aft", 100f);
        compB.waterVolume = 0f;
        flooding.compartments.add(compA);
        flooding.compartments.add(compB);

        hatches = new HatchComponent();
        Hatch hatch = new Hatch("hatch_fore_aft", 0.5f, "hold_fore", "hold_aft");
        hatch.isOpen = true;
        hatch.localPosition.set(0f, 0f, 5f);
        hatches.hatches.add(hatch);

        shipEntity.add(flooding);
        shipEntity.add(hatches);
        engine.addEntity(shipEntity);
    }

    @Test
    void waterFlowsThroughOpenHatch() {
        engine.update(1.0f);

        Compartment fore = findComp("hold_fore");
        Compartment aft = findComp("hold_aft");
        assertTrue(fore.waterVolume < 50f);
        assertTrue(aft.waterVolume > 0f);
    }

    @Test
    void noFlowThroughClosedHatch() {
        hatches.hatches.get(0).isOpen = false;

        engine.update(1.0f);

        Compartment fore = findComp("hold_fore");
        Compartment aft = findComp("hold_aft");
        assertEquals(50f, fore.waterVolume, 0.001f);
        assertEquals(0f, aft.waterVolume, 0.001f);
    }

    @Test
    void flowDirectionFollowsHeadDifference() {
        findComp("hold_fore").waterVolume = 10f;
        findComp("hold_aft").waterVolume = 80f;

        engine.update(1.0f);

        assertTrue(findComp("hold_fore").waterVolume > 10f);
        assertTrue(findComp("hold_aft").waterVolume < 80f);
    }

    @Test
    void waterVolumeNeverExceedsCapacity() {
        findComp("hold_fore").waterVolume = 95f;
        findComp("hold_aft").waterVolume = 95f;
        findComp("hold_aft").volume = 100f;

        engine.update(10.0f);

        assertTrue(findComp("hold_aft").waterVolume <= 100f);
        assertTrue(findComp("hold_fore").waterVolume >= 0f);
    }

    private Compartment findComp(String id) {
        for (int i = 0; i < flooding.compartments.size; i++) {
            if (id.equals(flooding.compartments.get(i).id)) {
                return flooding.compartments.get(i);
            }
        }
        return null;
    }
}
