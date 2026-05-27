package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitySnapshotBuilderTest {

    @Test
    void collectsSnapshotsFromEntitiesWithPersistenceId() {
        Engine engine = new Engine();

        Entity player = new Entity();
        PersistenceIdComponent pid = new PersistenceIdComponent();
        player.add(pid);

        TransformComponent tc = new TransformComponent();
        tc.position.set(10f, 20f, 30f);
        player.add(tc);

        HealthComponent hc = new HealthComponent();
        hc.currentHP = 75f;
        hc.maxHP = 100f;
        player.add(hc);

        engine.addEntity(player);

        EntitySnapshotBuilder builder = new EntitySnapshotBuilder();
        List<EntitySnapshot> snapshots = builder.buildSnapshots(engine, 0.0, 0.0, 0.0);

        assertEquals(1, snapshots.size());
        EntitySnapshot snap = snapshots.get(0);
        assertEquals(pid.uuid, snap.entityId);

        TransformSnapshot ts = snap.getSnapshot("Transform", TransformSnapshot.class);
        assertNotNull(ts);
        assertEquals(10.0, ts.galaxyX, 1e-5);

        HealthSnapshot hs = snap.getSnapshot("Health", HealthSnapshot.class);
        assertNotNull(hs);
        assertEquals(75f, hs.currentHP);
    }

    @Test
    void skipsEntitiesWithoutPersistenceId() {
        Engine engine = new Engine();

        Entity particle = new Entity();
        particle.add(new TransformComponent());
        engine.addEntity(particle);

        EntitySnapshotBuilder builder = new EntitySnapshotBuilder();
        List<EntitySnapshot> snapshots = builder.buildSnapshots(engine, 0.0, 0.0, 0.0);

        assertEquals(0, snapshots.size());
    }
}
