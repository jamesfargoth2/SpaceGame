-- Zone assignments: each zone owns a cuboid of galaxy-space
CREATE TABLE IF NOT EXISTS zone_assignments (
    zone_id         UUID PRIMARY KEY,
    zone_name       VARCHAR(255) NOT NULL,
    sector_min_x    DOUBLE PRECISION NOT NULL,
    sector_min_y    DOUBLE PRECISION NOT NULL,
    sector_min_z    DOUBLE PRECISION NOT NULL,
    sector_max_x    DOUBLE PRECISION NOT NULL,
    sector_max_y    DOUBLE PRECISION NOT NULL,
    sector_max_z    DOUBLE PRECISION NOT NULL,
    server_instance VARCHAR(255),
    adjacent_zones  UUID[] DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'DORMANT',
    boundary_overlap DOUBLE PRECISION NOT NULL DEFAULT 1000.0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'DORMANT', 'MIGRATING'))
);

CREATE INDEX IF NOT EXISTS idx_zone_assignments_status ON zone_assignments(status);

-- Players: persistent player data
CREATE TABLE IF NOT EXISTS players (
    player_id       UUID PRIMARY KEY,
    username        VARCHAR(255) NOT NULL UNIQUE,
    last_zone_id    UUID REFERENCES zone_assignments(zone_id),
    last_galaxy_x   DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_galaxy_y   DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_galaxy_z   DOUBLE PRECISION NOT NULL DEFAULT 0,
    inventory       JSONB DEFAULT '{}',
    wallet          JSONB DEFAULT '{"credits": 1000}',
    player_state    JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);
CREATE INDEX IF NOT EXISTS idx_players_last_zone ON players(last_zone_id);

-- Entities: persistent world entities (ships, stations, NPCs)
CREATE TABLE IF NOT EXISTS entities (
    entity_id       UUID PRIMARY KEY,
    zone_id         UUID REFERENCES zone_assignments(zone_id),
    entity_type     VARCHAR(100) NOT NULL,
    galaxy_x        DOUBLE PRECISION NOT NULL DEFAULT 0,
    galaxy_y        DOUBLE PRECISION NOT NULL DEFAULT 0,
    galaxy_z        DOUBLE PRECISION NOT NULL DEFAULT 0,
    component_state JSONB DEFAULT '{}',
    is_active       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_entities_zone ON entities(zone_id);
CREATE INDEX IF NOT EXISTS idx_entities_type ON entities(entity_type);
CREATE INDEX IF NOT EXISTS idx_entities_active ON entities(is_active);

-- Sector state: galaxy simulation state per zone/sector
CREATE TABLE IF NOT EXISTS sector_state (
    sector_id       UUID PRIMARY KEY REFERENCES zone_assignments(zone_id),
    faction_control JSONB DEFAULT '{}',
    resource_levels JSONB DEFAULT '{}',
    population      BIGINT NOT NULL DEFAULT 0,
    trade_demand    JSONB DEFAULT '{}',
    trade_supply    JSONB DEFAULT '{}',
    simulated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed a default zone for local development
INSERT INTO zone_assignments (zone_id, zone_name, sector_min_x, sector_min_y, sector_min_z, sector_max_x, sector_max_y, sector_max_z, server_instance, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'local-dev-zone', -50000, -50000, -50000, 50000, 50000, 50000, 'localhost:7100', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO sector_state (sector_id)
VALUES ('00000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;
