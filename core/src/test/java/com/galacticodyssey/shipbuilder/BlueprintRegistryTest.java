package com.galacticodyssey.shipbuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintRegistryTest {
    private BlueprintRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BlueprintRegistry();
        List<BlueprintData> blueprints = new ArrayList<>();

        BlueprintData cockpit = new BlueprintData();
        cockpit.blueprintId = "bp_cockpit";
        cockpit.type = BlueprintData.BlueprintType.ROOM;
        cockpit.unlocks = "COCKPIT";
        cockpit.rarity = BlueprintData.Rarity.COMMON;
        cockpit.shopPrice = 0;
        blueprints.add(cockpit);

        BlueprintData medbay = new BlueprintData();
        medbay.blueprintId = "bp_medbay";
        medbay.type = BlueprintData.BlueprintType.ROOM;
        medbay.unlocks = "MEDBAY";
        medbay.rarity = BlueprintData.Rarity.UNCOMMON;
        medbay.shopPrice = 15000;
        blueprints.add(medbay);

        BlueprintData laser = new BlueprintData();
        laser.blueprintId = "bp_laser_mk1";
        laser.type = BlueprintData.BlueprintType.MODULE;
        laser.unlocks = "laser_mk1";
        laser.rarity = BlueprintData.Rarity.COMMON;
        laser.shopPrice = 0;
        blueprints.add(laser);

        registry.loadFromData(blueprints, Arrays.asList("bp_cockpit", "bp_laser_mk1"));
    }

    @Test
    void isUnlocked_returnsTrueForStartingBlueprints() {
        assertTrue(registry.isUnlocked("bp_cockpit"));
        assertTrue(registry.isUnlocked("bp_laser_mk1"));
    }

    @Test
    void isUnlocked_returnsFalseForLockedBlueprints() {
        assertFalse(registry.isUnlocked("bp_medbay"));
    }

    @Test
    void unlock_makesBlueprintAvailable() {
        registry.unlock("bp_medbay");
        assertTrue(registry.isUnlocked("bp_medbay"));
    }

    @Test
    void getByType_filtersCorrectly() {
        List<BlueprintData> rooms = registry.getByType(BlueprintData.BlueprintType.ROOM);
        assertEquals(2, rooms.size());
        List<BlueprintData> modules = registry.getByType(BlueprintData.BlueprintType.MODULE);
        assertEquals(1, modules.size());
    }

    @Test
    void getUnlockedByType_filtersLockedOut() {
        List<BlueprintData> unlockedRooms = registry.getUnlockedByType(BlueprintData.BlueprintType.ROOM);
        assertEquals(1, unlockedRooms.size());
        assertEquals("bp_cockpit", unlockedRooms.get(0).blueprintId);
    }

    @Test
    void isRoomUnlocked_checksByRoomTypeName() {
        assertTrue(registry.isRoomUnlocked("COCKPIT"));
        assertFalse(registry.isRoomUnlocked("MEDBAY"));
    }
}
