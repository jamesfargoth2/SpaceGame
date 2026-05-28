package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityPersistenceService {

    public record EntityData(
            UUID entityId, UUID zoneId, String entityType,
            double galaxyX, double galaxyY, double galaxyZ,
            String componentStateJson, boolean isActive
    ) {}

    static final String UPSERT_ENTITY_SQL =
            "INSERT INTO entities (entity_id, zone_id, entity_type, galaxy_x, galaxy_y, galaxy_z, component_state, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?) " +
            "ON CONFLICT (entity_id) DO UPDATE SET " +
            "zone_id = EXCLUDED.zone_id, galaxy_x = EXCLUDED.galaxy_x, galaxy_y = EXCLUDED.galaxy_y, " +
            "galaxy_z = EXCLUDED.galaxy_z, component_state = EXCLUDED.component_state, " +
            "is_active = EXCLUDED.is_active, updated_at = NOW()";

    static final String LOAD_BY_ZONE_SQL =
            "SELECT entity_id, zone_id, entity_type, galaxy_x, galaxy_y, galaxy_z, " +
            "component_state::text, is_active FROM entities WHERE zone_id = ?";

    static final String UPDATE_POSITION_SQL =
            "UPDATE entities SET galaxy_x = ?, galaxy_y = ?, galaxy_z = ?, updated_at = NOW() WHERE entity_id = ?";

    static final String DELETE_ENTITY_SQL = "DELETE FROM entities WHERE entity_id = ?";

    private final DatabaseManager db;

    public EntityPersistenceService(DatabaseManager db) {
        this.db = db;
    }

    public List<EntityData> loadByZone(UUID zoneId) throws SQLException {
        List<EntityData> results = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_BY_ZONE_SQL)) {
            ps.setObject(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new EntityData(
                            rs.getObject("entity_id", UUID.class), rs.getObject("zone_id", UUID.class),
                            rs.getString("entity_type"), rs.getDouble("galaxy_x"),
                            rs.getDouble("galaxy_y"), rs.getDouble("galaxy_z"),
                            rs.getString(7), rs.getBoolean("is_active")));
                }
            }
        }
        return results;
    }

    public void upsertEntity(EntityData data) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_ENTITY_SQL)) {
            ps.setObject(1, data.entityId()); ps.setObject(2, data.zoneId());
            ps.setString(3, data.entityType()); ps.setDouble(4, data.galaxyX());
            ps.setDouble(5, data.galaxyY()); ps.setDouble(6, data.galaxyZ());
            ps.setString(7, data.componentStateJson()); ps.setBoolean(8, data.isActive());
            ps.executeUpdate();
        }
    }

    public void batchUpsert(List<EntityData> entities) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_ENTITY_SQL)) {
            conn.setAutoCommit(false);
            for (EntityData data : entities) {
                ps.setObject(1, data.entityId()); ps.setObject(2, data.zoneId());
                ps.setString(3, data.entityType()); ps.setDouble(4, data.galaxyX());
                ps.setDouble(5, data.galaxyY()); ps.setDouble(6, data.galaxyZ());
                ps.setString(7, data.componentStateJson()); ps.setBoolean(8, data.isActive());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public void batchUpdatePositions(List<EntityData> entities) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_POSITION_SQL)) {
            conn.setAutoCommit(false);
            for (EntityData data : entities) {
                ps.setDouble(1, data.galaxyX()); ps.setDouble(2, data.galaxyY());
                ps.setDouble(3, data.galaxyZ()); ps.setObject(4, data.entityId());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    public void deleteEntity(UUID entityId) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_ENTITY_SQL)) {
            ps.setObject(1, entityId);
            ps.executeUpdate();
        }
    }
}
