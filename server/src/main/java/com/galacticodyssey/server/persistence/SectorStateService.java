package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SectorStateService {

    public record SectorData(
            UUID sectorId, String factionControlJson, String resourceLevelsJson,
            long population, String tradeDemandJson, String tradeSupplyJson
    ) {}

    static final String UPSERT_SQL =
            "INSERT INTO sector_state (sector_id, faction_control, resource_levels, population, trade_demand, trade_supply, simulated_at) " +
            "VALUES (?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, NOW()) " +
            "ON CONFLICT (sector_id) DO UPDATE SET " +
            "faction_control = EXCLUDED.faction_control, resource_levels = EXCLUDED.resource_levels, " +
            "population = EXCLUDED.population, trade_demand = EXCLUDED.trade_demand, " +
            "trade_supply = EXCLUDED.trade_supply, simulated_at = NOW()";

    static final String LOAD_SQL =
            "SELECT sector_id, faction_control::text, resource_levels::text, population, " +
            "trade_demand::text, trade_supply::text FROM sector_state WHERE sector_id = ?";

    private final DatabaseManager db;

    public SectorStateService(DatabaseManager db) {
        this.db = db;
    }

    public SectorData load(UUID sectorId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_SQL)) {
            ps.setObject(1, sectorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new SectorData(
                        rs.getObject("sector_id", UUID.class), rs.getString(2), rs.getString(3),
                        rs.getLong("population"), rs.getString(5), rs.getString(6));
            }
        }
    }

    public void upsert(SectorData data) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setObject(1, data.sectorId()); ps.setString(2, data.factionControlJson());
            ps.setString(3, data.resourceLevelsJson()); ps.setLong(4, data.population());
            ps.setString(5, data.tradeDemandJson()); ps.setString(6, data.tradeSupplyJson());
            ps.executeUpdate();
        }
    }
}
