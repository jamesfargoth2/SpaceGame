package com.galacticodyssey.persistence;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceIdComponentTest {
    @Test
    void assignsUuidOnConstruction() {
        PersistenceIdComponent id = new PersistenceIdComponent();
        assertNotNull(id.uuid);
    }

    @Test
    void acceptsExplicitUuid() {
        UUID explicit = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PersistenceIdComponent id = new PersistenceIdComponent(explicit);
        assertEquals(explicit, id.uuid);
    }

    @Test
    void twoInstancesHaveDifferentUuids() {
        PersistenceIdComponent a = new PersistenceIdComponent();
        PersistenceIdComponent b = new PersistenceIdComponent();
        assertNotEquals(a.uuid, b.uuid);
    }
}
