package com.galacticodyssey.gateway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

public class ZoneRouter {

    public record RouteInfo(UUID zoneId, String serverAddress) {}

    static final String RESOLVE_BY_POSITION_SQL =
            "SELECT zone_id, server_instance FROM zone_assignments " +
            "WHERE status = 'ACTIVE' " +
            "AND sector_min_x <= ? AND sector_max_x >= ? " +
            "AND sector_min_y <= ? AND sector_max_y >= ? " +
            "AND sector_min_z <= ? AND sector_max_z >= ? " +
            "LIMIT 1";

    static final String RESOLVE_BY_ZONE_ID_SQL =
            "SELECT zone_id, server_instance FROM zone_assignments WHERE zone_id = ? AND status = 'ACTIVE'";

    static final String RESOLVE_BY_PLAYER_SQL =
            "SELECT z.zone_id, z.server_instance FROM players p " +
            "JOIN zone_assignments z ON p.last_zone_id = z.zone_id " +
            "WHERE p.username = ? AND z.status = 'ACTIVE'";

    private final Supplier<Connection> connectionSupplier;

    public ZoneRouter(Supplier<Connection> connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    public RouteInfo resolveByPosition(double x, double y, double z) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_POSITION_SQL)) {
            ps.setDouble(1, x); ps.setDouble(2, x);
            ps.setDouble(3, y); ps.setDouble(4, y);
            ps.setDouble(5, z); ps.setDouble(6, z);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(rs.getObject("zone_id", UUID.class), rs.getString("server_instance"));
            }
        }
    }

    public RouteInfo resolveByZoneId(UUID zoneId) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_ZONE_ID_SQL)) {
            ps.setObject(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(rs.getObject("zone_id", UUID.class), rs.getString("server_instance"));
            }
        }
    }

    public RouteInfo resolveByPlayer(String username) throws SQLException {
        try (Connection conn = connectionSupplier.get();
             PreparedStatement ps = conn.prepareStatement(RESOLVE_BY_PLAYER_SQL)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new RouteInfo(rs.getObject("zone_id", UUID.class), rs.getString("server_instance"));
            }
        }
    }
}
