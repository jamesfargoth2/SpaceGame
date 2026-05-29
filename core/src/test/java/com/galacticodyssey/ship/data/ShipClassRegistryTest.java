package com.galacticodyssey.ship.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipClassRegistryTest {

    // -------------------------------------------------------------------------
    // Ship class loading
    // -------------------------------------------------------------------------

    @Test
    void loadsShipClassFromJson() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[{\"id\":\"test_ship\",\"name\":\"Test Ship\",\"sizeClass\":\"SMALL\"," +
            "\"mass\":8000,\"linearThrust\":45000,\"strafeThrustFraction\":0.4," +
            "\"verticalThrustFraction\":0.5,\"pitchYawTorque\":18000,\"rollTorque\":12000," +
            "\"linearDrag\":0.15,\"angularDrag\":2.5,\"maxIsp\":3500,\"maxThrust\":45000," +
            "\"throttleResponseRate\":3.0,\"fuelCapacity\":800,\"wingArea\":20," +
            "\"dragCoefficient\":0.3,\"crossSectionArea\":8,\"stallAngle\":20," +
            "\"maxLiftCoefficient\":1.5,\"controlSurfaceAuthority\":0.9," +
            "\"vtolThrustFraction\":0.6,\"liftCurve\":[0,0.2,0.5,0.9,1.2,1.5,1.3,0.8,0.3,0.1]," +
            "\"defaultWeaponGroups\":[\"gun_left,gun_right\",\"missile_bay\"]}]";

        JsonValue root = new JsonReader().parse(json);
        registry.loadShipClasses(root);

        ShipClassData data = registry.getShipClass("test_ship");
        assertNotNull(data);
        assertEquals("Test Ship", data.name);
        assertEquals("SMALL", data.sizeClass);
        assertEquals(8000f, data.mass);
        assertEquals(45000f, data.linearThrust);
        assertEquals(0.4f, data.strafeThrustFraction, 0.001f);
        assertEquals(0.5f, data.verticalThrustFraction, 0.001f);
        assertEquals(18000f, data.pitchYawTorque);
        assertEquals(12000f, data.rollTorque);
        assertEquals(0.15f, data.linearDrag, 0.001f);
        assertEquals(2.5f, data.angularDrag, 0.001f);
        assertEquals(3500f, data.maxIsp);
        assertEquals(45000f, data.maxThrust);
        assertEquals(3.0f, data.throttleResponseRate, 0.001f);
        assertEquals(800f, data.fuelCapacity);
        assertEquals(20f, data.wingArea);
        assertEquals(0.3f, data.dragCoefficient, 0.001f);
        assertEquals(8f, data.crossSectionArea);
        assertEquals(20f, data.stallAngle);
        assertEquals(1.5f, data.maxLiftCoefficient, 0.001f);
        assertEquals(0.9f, data.controlSurfaceAuthority, 0.001f);
        assertEquals(0.6f, data.vtolThrustFraction, 0.001f);
    }

    @Test
    void loadsLiftCurveArray() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[{\"id\":\"lc_ship\",\"name\":\"LC Ship\",\"sizeClass\":\"SMALL\"," +
            "\"mass\":1,\"linearThrust\":1,\"strafeThrustFraction\":0,\"verticalThrustFraction\":0," +
            "\"pitchYawTorque\":0,\"rollTorque\":0,\"linearDrag\":0,\"angularDrag\":0," +
            "\"maxIsp\":0,\"maxThrust\":0,\"throttleResponseRate\":0,\"fuelCapacity\":0," +
            "\"wingArea\":0,\"dragCoefficient\":0,\"crossSectionArea\":0,\"stallAngle\":0," +
            "\"maxLiftCoefficient\":0,\"controlSurfaceAuthority\":0,\"vtolThrustFraction\":0," +
            "\"liftCurve\":[0.0,0.2,0.5,0.9,1.2,1.5,1.3,0.8,0.3,0.1]}]";

        registry.loadShipClasses(new JsonReader().parse(json));

        ShipClassData data = registry.getShipClass("lc_ship");
        assertNotNull(data.liftCurve);
        assertEquals(10, data.liftCurve.length);
        assertEquals(0.9f, data.liftCurve[3], 0.001f);
        assertEquals(1.5f, data.liftCurve[5], 0.001f);
    }

    @Test
    void loadsDefaultWeaponGroups() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[{\"id\":\"wg_ship\",\"name\":\"WG Ship\",\"sizeClass\":\"MEDIUM\"," +
            "\"mass\":1,\"linearThrust\":1,\"strafeThrustFraction\":0,\"verticalThrustFraction\":0," +
            "\"pitchYawTorque\":0,\"rollTorque\":0,\"linearDrag\":0,\"angularDrag\":0," +
            "\"maxIsp\":0,\"maxThrust\":0,\"throttleResponseRate\":0,\"fuelCapacity\":0," +
            "\"wingArea\":0,\"dragCoefficient\":0,\"crossSectionArea\":0,\"stallAngle\":0," +
            "\"maxLiftCoefficient\":0,\"controlSurfaceAuthority\":0,\"vtolThrustFraction\":0," +
            "\"defaultWeaponGroups\":[\"gun_left,gun_right\",\"missile_bay\"]}]";

        registry.loadShipClasses(new JsonReader().parse(json));

        ShipClassData data = registry.getShipClass("wg_ship");
        assertNotNull(data.defaultWeaponGroups);
        assertEquals(2, data.defaultWeaponGroups.length);
        assertEquals("gun_left,gun_right", data.defaultWeaponGroups[0]);
        assertEquals("missile_bay", data.defaultWeaponGroups[1]);
    }

    @Test
    void loadsMultipleShipClasses() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[" +
            "{\"id\":\"ship_a\",\"name\":\"A\",\"sizeClass\":\"SMALL\"," +
            "\"mass\":1,\"linearThrust\":1,\"strafeThrustFraction\":0,\"verticalThrustFraction\":0," +
            "\"pitchYawTorque\":0,\"rollTorque\":0,\"linearDrag\":0,\"angularDrag\":0," +
            "\"maxIsp\":0,\"maxThrust\":0,\"throttleResponseRate\":0,\"fuelCapacity\":0," +
            "\"wingArea\":0,\"dragCoefficient\":0,\"crossSectionArea\":0,\"stallAngle\":0," +
            "\"maxLiftCoefficient\":0,\"controlSurfaceAuthority\":0,\"vtolThrustFraction\":0}," +
            "{\"id\":\"ship_b\",\"name\":\"B\",\"sizeClass\":\"LARGE\"," +
            "\"mass\":2,\"linearThrust\":2,\"strafeThrustFraction\":0,\"verticalThrustFraction\":0," +
            "\"pitchYawTorque\":0,\"rollTorque\":0,\"linearDrag\":0,\"angularDrag\":0," +
            "\"maxIsp\":0,\"maxThrust\":0,\"throttleResponseRate\":0,\"fuelCapacity\":0," +
            "\"wingArea\":0,\"dragCoefficient\":0,\"crossSectionArea\":0,\"stallAngle\":0," +
            "\"maxLiftCoefficient\":0,\"controlSurfaceAuthority\":0,\"vtolThrustFraction\":0}" +
            "]";

        registry.loadShipClasses(new JsonReader().parse(json));

        assertNotNull(registry.getShipClass("ship_a"));
        assertNotNull(registry.getShipClass("ship_b"));
        assertEquals("SMALL", registry.getShipClass("ship_a").sizeClass);
        assertEquals("LARGE", registry.getShipClass("ship_b").sizeClass);
    }

    @Test
    void parsesEdFlightFieldsWithDefaults() {
        String json = "[{"
            + "\"id\":\"x\",\"name\":\"X\",\"sizeClass\":\"SMALL\",\"mass\":5000,"
            + "\"linearThrust\":50000,\"strafeThrustFraction\":0.6,\"verticalThrustFraction\":0.6,"
            + "\"pitchYawTorque\":20000,\"rollTorque\":15000,\"linearDrag\":0.6,\"angularDrag\":0.5,"
            + "\"maxIsp\":300,\"maxThrust\":50000,\"throttleResponseRate\":3,\"fuelCapacity\":100,"
            + "\"wingArea\":10,\"dragCoefficient\":0.3,\"crossSectionArea\":5,\"stallAngle\":15,"
            + "\"maxLiftCoefficient\":1.2,\"controlSurfaceAuthority\":1,\"vtolThrustFraction\":0.5,"
            + "\"boostSpeedMultiplier\":1.7}]";
        com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
        ShipClassRegistry reg = new ShipClassRegistry();
        reg.loadShipClasses(root);
        ShipClassData d = reg.getShipClass("x");

        assertEquals(1.7f, d.boostSpeedMultiplier, 1e-4); // explicit value parsed
        assertEquals(0.4f, d.reverseFraction, 1e-4);       // default applied (absent in JSON)
        assertEquals(0.4f, d.blueZoneLow, 1e-4);
        assertEquals(0.8f, d.blueZoneHigh, 1e-4);
    }

    // -------------------------------------------------------------------------
    // Atmosphere profile loading
    // -------------------------------------------------------------------------

    @Test
    void loadsAtmosphereProfileFromJson() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[{\"id\":\"earth_like\",\"name\":\"Earth-Like\"," +
            "\"surfaceDensity\":1.225,\"scaleHeight\":8500," +
            "\"speedOfSound\":343,\"machThreshold\":3.0,\"composition\":\"N2/O2\"}]";

        JsonValue root = new JsonReader().parse(json);
        registry.loadAtmosphereProfiles(root);

        AtmosphereProfile profile = registry.getAtmosphereProfile("earth_like");
        assertNotNull(profile);
        assertEquals("Earth-Like", profile.name);
        assertEquals(1.225f, profile.surfaceDensity, 0.001f);
        assertEquals(8500f, profile.scaleHeight);
        assertEquals(343f, profile.speedOfSound);
        assertEquals(3.0f, profile.machThreshold, 0.001f);
        assertEquals("N2/O2", profile.composition);
    }

    @Test
    void loadsMultipleAtmosphereProfiles() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[" +
            "{\"id\":\"thin\",\"name\":\"Thin\",\"surfaceDensity\":0.02,\"scaleHeight\":11100," +
            "\"speedOfSound\":240,\"machThreshold\":5.0,\"composition\":\"CO2\"}," +
            "{\"id\":\"dense\",\"name\":\"Dense\",\"surfaceDensity\":65.0,\"scaleHeight\":15900," +
            "\"speedOfSound\":410,\"machThreshold\":2.0,\"composition\":\"CO2/SO2\"}" +
            "]";

        registry.loadAtmosphereProfiles(new JsonReader().parse(json));

        assertNotNull(registry.getAtmosphereProfile("thin"));
        assertNotNull(registry.getAtmosphereProfile("dense"));
        assertEquals(65.0f, registry.getAtmosphereProfile("dense").surfaceDensity, 0.001f);
    }

    @Test
    void compositionDefaultsToEmptyStringWhenAbsent() {
        ShipClassRegistry registry = new ShipClassRegistry();
        String json = "[{\"id\":\"no_comp\",\"name\":\"No Comp\",\"surfaceDensity\":1.0," +
            "\"scaleHeight\":8000,\"speedOfSound\":300,\"machThreshold\":2.5}]";

        registry.loadAtmosphereProfiles(new JsonReader().parse(json));

        AtmosphereProfile profile = registry.getAtmosphereProfile("no_comp");
        assertNotNull(profile);
        assertEquals("", profile.composition);
    }

    // -------------------------------------------------------------------------
    // Null / missing lookups
    // -------------------------------------------------------------------------

    @Test
    void returnsNullForUnknownShipClass() {
        ShipClassRegistry registry = new ShipClassRegistry();
        assertNull(registry.getShipClass("nonexistent"));
    }

    @Test
    void returnsNullForUnknownAtmosphereProfile() {
        ShipClassRegistry registry = new ShipClassRegistry();
        assertNull(registry.getAtmosphereProfile("nonexistent"));
    }

    @Test
    void returnsNullForBothBeforeAnyLoad() {
        ShipClassRegistry registry = new ShipClassRegistry();
        assertNull(registry.getShipClass("anything"));
        assertNull(registry.getAtmosphereProfile("anything"));
    }
}
