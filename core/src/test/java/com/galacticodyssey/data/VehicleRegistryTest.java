package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleRegistryTest {
    private static final String JSON = "{ \"vehicles\": [\n" +
        "  { \"id\": \"rover_light\", \"displayName\": \"Light Rover\", \"modelPath\": \"models/rover.g3db\"," +
        "    \"sizeClass\": \"LIGHT\", \"mass\": 900, \"maxDriveForce\": 6000, \"maxSteerAngle\": 40," +
        "    \"maxHP\": 200, \"armorValue\": 5, \"baySlots\": 1," +
        "    \"weapon\": { \"damage\": 25, \"fireRate\": 5, \"range\": 100, \"hitscan\": true," +
        "      \"damageType\": \"BALLISTIC\", \"firingMode\": \"AUTO\", \"magSize\": 80, \"reloadTime\": 2.0 } }\n" +
        "] }";

    @Test
    void loadsDefinitionsById() {
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        VehicleDefinition def = reg.get("rover_light");
        assertNotNull(def);
        assertEquals("Light Rover", def.displayName);
        assertEquals(900f, def.mass);
        assertEquals(1, def.baySlots);
        assertNotNull(def.weapon);
        assertEquals(DamageType.BALLISTIC, def.weapon.damageType);
        assertEquals(80, def.weapon.magSize);
    }

    @Test
    void unknownIdReturnsNull() {
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        assertNull(reg.get("nope"));
    }
}
