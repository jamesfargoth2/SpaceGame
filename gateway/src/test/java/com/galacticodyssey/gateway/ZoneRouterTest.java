package com.galacticodyssey.gateway;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ZoneRouterTest {
    @Test
    void routeInfoHoldsFields() {
        UUID zoneId = UUID.randomUUID();
        ZoneRouter.RouteInfo info = new ZoneRouter.RouteInfo(zoneId, "10.0.0.5:7100");
        assertEquals(zoneId, info.zoneId());
        assertEquals("10.0.0.5:7100", info.serverAddress());
    }

    @Test
    void resolveByPositionSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_POSITION_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("zone_assignments"));
        assertTrue(sql.contains("sector_min_x"));
        assertTrue(sql.contains("ACTIVE"));
    }

    @Test
    void resolveByZoneIdSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_ZONE_ID_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("zone_assignments"));
        assertTrue(sql.contains("zone_id"));
    }

    @Test
    void resolveByPlayerSqlIsValid() {
        String sql = ZoneRouter.RESOLVE_BY_PLAYER_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("players"));
        assertTrue(sql.contains("zone_assignments"));
    }
}
