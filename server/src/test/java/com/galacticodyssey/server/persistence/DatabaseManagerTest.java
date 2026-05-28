package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @Test
    void configHasDefaults() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        assertEquals("localhost", config.host);
        assertEquals(5432, config.port);
        assertEquals("galactic_odyssey", config.database);
        assertEquals("galactic", config.username);
        assertEquals("dev_only", config.password);
        assertEquals(10, config.maxPoolSize);
    }

    @Test
    void buildJdbcUrl() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        config.host = "db.example.com";
        config.port = 5433;
        config.database = "test_db";
        assertEquals("jdbc:postgresql://db.example.com:5433/test_db", config.getJdbcUrl());
    }

    @Test
    void createManagerDoesNotThrowBeforeInit() {
        DatabaseManager.Config config = new DatabaseManager.Config();
        DatabaseManager manager = new DatabaseManager(config);
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    void shutdownWithoutInitIsNoOp() {
        DatabaseManager manager = new DatabaseManager(new DatabaseManager.Config());
        assertDoesNotThrow(manager::shutdown);
    }
}
