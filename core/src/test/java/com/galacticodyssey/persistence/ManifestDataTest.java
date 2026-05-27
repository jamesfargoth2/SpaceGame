package com.galacticodyssey.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManifestDataTest {

    @Test
    void constructorSetsDisplayFields() {
        UUID playerId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        ManifestData m = new ManifestData("test-save", 42L, playerId, systemId);
        m.displayName = "Save #1 — Sol System";
        m.locationName = "Sol System";
        m.locationDetail = "Docked at Haven Station";
        m.playtimeSeconds = 45000L;
        m.playerCredits = 12500L;
        m.shipName = "Cobra Mk III";

        assertEquals("Save #1 — Sol System", m.displayName);
        assertEquals("Sol System", m.locationName);
        assertEquals("Docked at Haven Station", m.locationDetail);
        assertEquals(45000L, m.playtimeSeconds);
        assertEquals(12500L, m.playerCredits);
        assertEquals("Cobra Mk III", m.shipName);
    }

    @Test
    void noArgConstructorDefaultsDisplayFieldsToNull() {
        ManifestData m = new ManifestData();
        assertNull(m.displayName);
        assertNull(m.locationName);
        assertNull(m.locationDetail);
        assertEquals(0L, m.playtimeSeconds);
        assertEquals(0L, m.playerCredits);
        assertNull(m.shipName);
    }

    @Test
    void isAutosaveDetectsAutosaveSlotNames() {
        ManifestData auto = new ManifestData();
        auto.saveName = "autosave-0";
        assertTrue(auto.isAutosave());

        ManifestData manual = new ManifestData();
        manual.saveName = "save-2026-05-27-143200";
        assertFalse(manual.isAutosave());
    }

    @Test
    void getDisplayNameFallsBackToSaveName() {
        ManifestData m = new ManifestData();
        m.saveName = "my-save";
        m.displayName = null;
        assertEquals("my-save", m.getDisplayNameOrFallback());

        m.displayName = "Custom Name";
        assertEquals("Custom Name", m.getDisplayNameOrFallback());
    }
}
