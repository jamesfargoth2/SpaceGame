package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.*;
import com.galacticodyssey.water.data.*;
import com.galacticodyssey.water.events.DepthZoneChangedEvent;
import com.galacticodyssey.water.events.PressureDamageEvent;
import com.galacticodyssey.water.events.AscentSicknessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnderwaterSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private SwimmingStateComponent swimState;
    private DepthZoneComponent depthZone;

    private final List<DepthZoneChangedEvent> zoneEvents = new ArrayList<>();
    private final List<PressureDamageEvent> pressureEvents = new ArrayList<>();
    private final List<AscentSicknessEvent> sicknessEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();
        registry.setSwimConfig(new SwimConfigData());

        DepthZonesConfig dzConfig = new DepthZonesConfig();
        dzConfig.noGearMaxPressure = 2.0f;
        dzConfig.zones = new DepthZoneData[5];

        dzConfig.zones[0] = makeZone("SUNLIT", 0, 10, 0);
        dzConfig.zones[1] = makeZone("TWILIGHT", 10, 50, 0.5f);
        dzConfig.zones[2] = makeZone("MIDNIGHT", 50, 200, 2.0f);
        dzConfig.zones[3] = makeZone("ABYSSAL", 200, 500, 5.0f);
        dzConfig.zones[4] = makeZone("HADAL", 500, 99999, 10.0f);
        registry.setDepthZonesConfig(dzConfig);

        UnderwaterSystem system = new UnderwaterSystem(16, eventBus, registry);
        engine = new Engine();
        engine.addSystem(system);

        player = new Entity();
        swimState = new SwimmingStateComponent();
        depthZone = new DepthZoneComponent();
        player.add(swimState);
        player.add(depthZone);
        engine.addEntity(player);

        eventBus.subscribe(DepthZoneChangedEvent.class, zoneEvents::add);
        eventBus.subscribe(PressureDamageEvent.class, pressureEvents::add);
        eventBus.subscribe(AscentSicknessEvent.class, sicknessEvents::add);
    }

    private DepthZoneData makeZone(String id, float min, float max, float pressureDmg) {
        DepthZoneData z = new DepthZoneData();
        z.id = id;
        z.minDepth = min;
        z.maxDepth = max;
        z.pressureDamageRate = pressureDmg;
        z.visibilityStart = 1.0f;
        z.visibilityEnd = 0.5f;
        z.fogColorR = 0.1f;
        z.fogColorG = 0.3f;
        z.fogColorB = 0.6f;
        z.requiresLight = min >= 50;
        return z;
    }

    @Test
    void pressureCalculationAtKnownDepths() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 100f;

        engine.update(1f / 60f);

        assertEquals(11.0f, depthZone.ambientPressure, 0.01f);
    }

    @Test
    void sunlitZoneAtShallowDepth() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 5f;

        engine.update(1f / 60f);

        assertEquals(DepthZone.SUNLIT, depthZone.currentZone);
    }

    @Test
    void twilightZoneAtMidDepth() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 30f;
        depthZone.currentZone = DepthZone.SUNLIT;

        engine.update(1f / 60f);

        assertEquals(DepthZone.TWILIGHT, depthZone.currentZone);
        assertEquals(1, zoneEvents.size());
        assertEquals(DepthZone.SUNLIT, zoneEvents.get(0).previousZone);
        assertEquals(DepthZone.TWILIGHT, zoneEvents.get(0).newZone);
    }

    @Test
    void abyssalZoneAtGreatDepth() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 300f;

        engine.update(1f / 60f);

        assertEquals(DepthZone.ABYSSAL, depthZone.currentZone);
    }

    @Test
    void noPressureDamageInSunlitWithNoGear() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 5f;

        engine.update(1.0f);

        assertTrue(pressureEvents.isEmpty());
    }

    @Test
    void pressureDamageWithoutGearBelowGraceDepth() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 50f;

        engine.update(1.0f);

        assertFalse(pressureEvents.isEmpty());
        assertTrue(pressureEvents.get(0).damage > 0f);
    }

    @Test
    void noPressureDamageWithAdequateGear() {
        DiveGearComponent gear = new DiveGearComponent();
        gear.maxPressure = 20f;
        player.add(gear);

        swimState.swimState = SwimState.SUBMERGED;
        swimState.currentDepth = 50f;

        engine.update(1.0f);

        assertTrue(pressureEvents.isEmpty());
    }

    @Test
    void noZoneUpdateWhenDry() {
        swimState.swimState = SwimState.DRY;
        swimState.currentDepth = 0f;

        engine.update(1f / 60f);

        assertTrue(zoneEvents.isEmpty());
    }

    @Test
    void ascentSicknessOnFastRise() {
        swimState.swimState = SwimState.DIVING;
        swimState.currentDepth = 35f;
        swimState.verticalSpeed = 15f;

        engine.update(1f / 60f);

        assertEquals(1, sicknessEvents.size());
        assertTrue(sicknessEvents.get(0).duration >= 5.0f);
    }
}
