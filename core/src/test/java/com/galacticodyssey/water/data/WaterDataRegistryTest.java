package com.galacticodyssey.water.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterDataRegistryTest {

    private WaterDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WaterDataRegistry();
    }

    @Test
    void programmaticSwimConfigRegistration() {
        SwimConfigData config = new SwimConfigData();
        config.surfaceSwimSpeed = 3.0f;
        config.maxBreath = 45f;

        registry.setSwimConfig(config);

        assertEquals(3.0f, registry.getSwimConfig().surfaceSwimSpeed, 0.001f);
        assertEquals(45f, registry.getSwimConfig().maxBreath, 0.001f);
    }

    @Test
    void programmaticDiveGearRegistration() {
        DiveGearDefinition gear = new DiveGearDefinition();
        gear.id = "test_rebreather";
        gear.name = "Test Rebreather";
        gear.oxygenCapacity = 500f;
        gear.maxPressure = 10f;
        gear.providesLight = false;
        gear.lightRadius = 0f;
        gear.swimSpeedModifier = 1.0f;

        registry.registerDiveGear(gear);

        DiveGearDefinition result = registry.getDiveGear("test_rebreather");
        assertNotNull(result);
        assertEquals(500f, result.oxygenCapacity, 0.001f);
        assertEquals(10f, result.maxPressure, 0.001f);
    }

    @Test
    void programmaticWeatherProfileRegistration() {
        WeatherProfileData profile = new WeatherProfileData();
        profile.phase = "STORM";
        profile.waveCount = 6;
        profile.maxAmplitude = 6.0f;

        registry.registerWeatherProfile(profile);

        WeatherProfileData result = registry.getWeatherProfile("STORM");
        assertNotNull(result);
        assertEquals(6, result.waveCount);
        assertEquals(6.0f, result.maxAmplitude, 0.001f);
    }

    @Test
    void programmaticDepthZonesRegistration() {
        DepthZonesConfig config = new DepthZonesConfig();
        config.maxVisibilityDistance = 150f;
        config.noGearMaxPressure = 2.0f;
        config.zones = new DepthZoneData[1];
        config.zones[0] = new DepthZoneData();
        config.zones[0].id = "SUNLIT";
        config.zones[0].minDepth = 0f;
        config.zones[0].maxDepth = 10f;

        registry.setDepthZonesConfig(config);

        assertEquals(150f, registry.getDepthZonesConfig().maxVisibilityDistance, 0.001f);
        assertEquals(1, registry.getDepthZonesConfig().zones.length);
        assertEquals("SUNLIT", registry.getDepthZonesConfig().zones[0].id);
    }

    @Test
    void unknownDiveGearReturnsNull() {
        assertNull(registry.getDiveGear("nonexistent"));
    }

    @Test
    void unknownWeatherProfileReturnsNull() {
        assertNull(registry.getWeatherProfile("TORNADO"));
    }
}
