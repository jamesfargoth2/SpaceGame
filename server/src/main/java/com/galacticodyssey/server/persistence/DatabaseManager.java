package com.galacticodyssey.server.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    public static class Config {
        public String host = "localhost";
        public int port = 5432;
        public String database = "galactic_odyssey";
        public String username = "galactic";
        public String password = "dev_only";
        public int maxPoolSize = 10;

        public String getJdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
    }

    private final Config config;
    private HikariDataSource dataSource;

    public DatabaseManager(Config config) {
        this.config = config;
    }

    public void init() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.username);
        hikari.setPassword(config.password);
        hikari.setMaximumPoolSize(config.maxPoolSize);
        hikari.setPoolName("galactic-odyssey-pool");
        dataSource = new HikariDataSource(hikari);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseManager not initialized — call init() first");
        }
        return dataSource.getConnection();
    }

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
