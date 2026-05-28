package com.galacticodyssey.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleDefinitionTest {
    @Test
    void holdsPhysicsSurvivalAndWeaponFields() {
        VehicleDefinition def = new VehicleDefinition();
        def.id = "rover_light";
        def.maxHP = 250f;
        def.armorValue = 10f;
        def.baySlots = 1;
        def.weapon = new VehicleDefinition.VehicleWeaponStats();
        def.weapon.damage = 30f;
        def.weapon.fireRate = 4f;
        def.weapon.hitscan = true;
        def.weapon.damageType = DamageType.BALLISTIC;
        def.weapon.magSize = 60;

        assertEquals("rover_light", def.id);
        assertEquals(250f, def.maxHP);
        assertEquals(1, def.baySlots);
        assertEquals(DamageType.BALLISTIC, def.weapon.damageType);
        assertEquals(60, def.weapon.magSize);
    }
}
