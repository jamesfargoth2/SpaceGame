package com.galacticodyssey.server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerPersistenceService {

    public record PlayerData(
            UUID playerId, String username, UUID lastZoneId,
            double lastGalaxyX, double lastGalaxyY, double lastGalaxyZ,
            String inventoryJson, String walletJson, String playerStateJson
    ) {}

    static final String UPSERT_POSITION_SQL =
            "INSERT INTO players (player_id, username, last_zone_id, last_galaxy_x, last_galaxy_y, last_galaxy_z) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (player_id) DO UPDATE SET " +
            "last_zone_id = EXCLUDED.last_zone_id, " +
            "last_galaxy_x = EXCLUDED.last_galaxy_x, " +
            "last_galaxy_y = EXCLUDED.last_galaxy_y, " +
            "last_galaxy_z = EXCLUDED.last_galaxy_z, " +
            "last_login = NOW()";

    static final String LOAD_BY_USERNAME_SQL =
            "SELECT player_id, username, last_zone_id, last_galaxy_x, last_galaxy_y, last_galaxy_z, " +
            "inventory::text, wallet::text, player_state::text FROM players WHERE username = ?";

    static final String UPDATE_POSITION_SQL =
            "UPDATE players SET last_zone_id = ?, last_galaxy_x = ?, last_galaxy_y = ?, last_galaxy_z = ?, " +
            "last_login = NOW() WHERE player_id = ?";

    static final String SAVE_INVENTORY_SQL =
            "UPDATE players SET inventory = ?::jsonb WHERE player_id = ?";

    static final String SAVE_WALLET_SQL =
            "UPDATE players SET wallet = ?::jsonb WHERE player_id = ?";

    private final DatabaseManager db;

    public PlayerPersistenceService(DatabaseManager db) {
        this.db = db;
    }

    public PlayerData loadByUsername(String username) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_BY_USERNAME_SQL)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PlayerData(
                        rs.getObject("player_id", UUID.class), rs.getString("username"),
                        rs.getObject("last_zone_id", UUID.class),
                        rs.getDouble("last_galaxy_x"), rs.getDouble("last_galaxy_y"), rs.getDouble("last_galaxy_z"),
                        rs.getString(7), rs.getString(8), rs.getString(9));
            }
        }
    }

    public void upsertPosition(UUID playerId, String username, UUID zoneId,
                               double x, double y, double z) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_POSITION_SQL)) {
            ps.setObject(1, playerId);
            ps.setString(2, username);
            ps.setObject(3, zoneId);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.executeUpdate();
        }
    }

    public void updatePosition(UUID playerId, UUID zoneId, double x, double y, double z) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_POSITION_SQL)) {
            ps.setObject(1, zoneId);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setObject(5, playerId);
            ps.executeUpdate();
        }
    }

    public void saveInventory(UUID playerId, String inventoryJson) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SAVE_INVENTORY_SQL)) {
            ps.setString(1, inventoryJson);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }

    public void saveWallet(UUID playerId, String walletJson) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SAVE_WALLET_SQL)) {
            ps.setString(1, walletJson);
            ps.setObject(2, playerId);
            ps.executeUpdate();
        }
    }
}
