package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPersistenceServiceTest {
    @Test
    void playerDataHoldsFields() {
        UUID playerId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        PlayerPersistenceService.PlayerData data = new PlayerPersistenceService.PlayerData(
                playerId, "testuser", zoneId, 100.0, 200.0, 300.0, "{}", "{\"credits\":500}", "{}");
        assertEquals(playerId, data.playerId());
        assertEquals("testuser", data.username());
        assertEquals(zoneId, data.lastZoneId());
        assertEquals(100.0, data.lastGalaxyX());
    }

    @Test
    void upsertSqlIsValid() {
        String sql = PlayerPersistenceService.UPSERT_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO players"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadSqlIsValid() {
        String sql = PlayerPersistenceService.LOAD_BY_USERNAME_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("FROM players"));
    }

    @Test
    void updatePositionSqlIsValid() {
        String sql = PlayerPersistenceService.UPDATE_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE players"));
        assertTrue(sql.contains("last_galaxy_x"));
    }
}
