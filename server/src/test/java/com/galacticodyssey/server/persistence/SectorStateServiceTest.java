package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SectorStateServiceTest {
    @Test
    void sectorDataHoldsFields() {
        UUID sectorId = UUID.randomUUID();
        SectorStateService.SectorData data = new SectorStateService.SectorData(
                sectorId, "{\"federation\":60}", "{\"iron\":1000}", 50000,
                "{\"fuel\":100}", "{\"fuel\":80}");
        assertEquals(sectorId, data.sectorId());
        assertEquals(50000, data.population());
    }

    @Test
    void upsertSqlIsValid() {
        String sql = SectorStateService.UPSERT_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO sector_state"));
        assertTrue(sql.contains("ON CONFLICT"));
    }

    @Test
    void loadSqlIsValid() {
        String sql = SectorStateService.LOAD_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("SELECT"));
        assertTrue(sql.contains("sector_state"));
    }
}
