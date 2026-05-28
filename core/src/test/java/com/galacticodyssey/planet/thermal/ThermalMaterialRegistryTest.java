package com.galacticodyssey.planet.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalMaterialRegistryTest {

    @Test
    void registersAndRetrievesMaterialById() {
        ThermalMaterialRegistry registry = new ThermalMaterialRegistry();
        ThermalMaterial wood = new ThermalMaterial();
        wood.id = "wood";
        wood.ignitionPoint = 573f;
        wood.flammable = true;
        registry.register(wood);

        ThermalMaterial fetched = registry.get("wood");
        assertNotNull(fetched);
        assertEquals(573f, fetched.ignitionPoint, 0.001f);
        assertTrue(fetched.flammable);
        assertNull(registry.get("missing"));
    }
}
