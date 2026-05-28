package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EntityPersistenceServiceTest {
    @Test
    void entityDataHoldsFields() {
        UUID entityId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        EntityPersistenceService.EntityData data = new EntityPersistenceService.EntityData(
                entityId, zoneId, "ship", 10.0, 20.0, 30.0, "{\"health\":100}", true);
        assertEquals(entityId, data.entityId());
        assertEquals("ship", data.entityType());
        assertTrue(data.isActive());
    }

    @Test
    void batchUpsertSqlIsValid() {
        String sql = EntityPersistenceService.UPSERT_ENTITY_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO entities"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadByZoneSqlIsValid() {
        String sql = EntityPersistenceService.LOAD_BY_ZONE_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("zone_id"));
    }

    @Test
    void updatePositionBatchSqlIsValid() {
        String sql = EntityPersistenceService.UPDATE_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("UPDATE entities"));
        assertTrue(sql.contains("galaxy_x"));
    }
}
