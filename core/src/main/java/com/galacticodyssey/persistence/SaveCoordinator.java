package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.LoadCompleteEvent;
import com.galacticodyssey.core.events.SaveBeginEvent;
import com.galacticodyssey.core.events.SaveCompleteEvent;
import com.galacticodyssey.core.events.SaveFailedEvent;
import com.galacticodyssey.persistence.migration.SaveMigrator;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import com.galacticodyssey.persistence.SnapshotComponentRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the full save/load flow and manages the auto-save timer.
 *
 * <p>On save, it snapshots every {@link PersistenceIdComponent}-tagged entity via
 * {@link EntitySnapshotBuilder}, writes to the configured {@link SaveBackend}, and
 * publishes {@link SaveCompleteEvent} or {@link SaveFailedEvent}.</p>
 *
 * <p>On load, it clears existing persistent entities, restores them from the
 * {@link SaveBundle}, resolves inter-entity references via {@link ReferenceResolver},
 * and publishes {@link LoadCompleteEvent}.</p>
 *
 * <p>Auto-save cycles through {@value #AUTO_SAVE_SLOT_COUNT} named slots every
 * {@link #DEFAULT_AUTO_SAVE_INTERVAL} seconds (configurable).</p>
 */
public class SaveCoordinator {

    private static final int AUTO_SAVE_SLOT_COUNT = 3;
    private static final float DEFAULT_AUTO_SAVE_INTERVAL = 300f;

    private final EventBus eventBus;
    private final Engine engine;
    private final SaveBackend backend;
    private final EntitySnapshotBuilder snapshotBuilder;
    private final ReferenceResolver referenceResolver;
    private final SaveMigrator migrator;

    private final long galaxySeed;
    private final UUID playerEntityId;
    private final CoordinateManager coordinateManager;

    private float autoSaveTimer;
    private float autoSaveInterval = DEFAULT_AUTO_SAVE_INTERVAL;
    private int autoSaveSlotIndex;
    private boolean autoSaveEnabled = true;

    private static final ComponentMapper<PersistenceIdComponent> pidMapper =
        ComponentMapper.getFor(PersistenceIdComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    /**
     * @param eventBus          central event bus — receives save/load lifecycle events
     * @param engine            Ashley ECS engine whose entities are snapshotted
     * @param backend           storage backend (file, memory, etc.)
     * @param galaxySeed        seed embedded in every save manifest
     * @param playerEntityId    UUID of the player entity (routed to {@link SaveBundle#playerSnapshot})
     * @param coordinateManager provides floating-origin offsets; may be {@code null}
     *                          (treated as zero offset)
     */
    public SaveCoordinator(EventBus eventBus, Engine engine, SaveBackend backend,
                           long galaxySeed, UUID playerEntityId,
                           CoordinateManager coordinateManager) {
        this.eventBus = eventBus;
        this.engine = engine;
        this.backend = backend;
        this.galaxySeed = galaxySeed;
        this.playerEntityId = playerEntityId;
        this.coordinateManager = coordinateManager;
        this.snapshotBuilder = new EntitySnapshotBuilder();
        this.referenceResolver = new ReferenceResolver();
        this.migrator = new SaveMigrator();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Persist the current game state under the given save name. */
    public void save(String saveName) {
        long startTime = System.currentTimeMillis();
        eventBus.publish(new SaveBeginEvent(saveName));

        try {
            double ox = originX();
            double oy = originY();
            double oz = originZ();

            SaveBundle bundle = new SaveBundle();
            bundle.manifest = new ManifestData(saveName, galaxySeed, playerEntityId, null);

            List<EntitySnapshot> allSnapshots = snapshotBuilder.buildSnapshots(engine, ox, oy, oz);

            for (EntitySnapshot snap : allSnapshots) {
                if (snap.entityId.equals(playerEntityId)) {
                    bundle.playerSnapshot = snap;
                } else {
                    bundle.ownedShipSnapshots.add(snap);
                }
            }

            backend.writeSave(saveName, bundle);

            long duration = System.currentTimeMillis() - startTime;
            eventBus.publish(new SaveCompleteEvent(saveName, duration));
        } catch (Exception e) {
            eventBus.publish(new SaveFailedEvent(saveName, e));
        }
    }

    /**
     * Load game state from the named save.
     *
     * <p>Existing persistent entities are removed from the engine before restoration
     * to avoid duplicates.</p>
     *
     * @throws RuntimeException wrapping any I/O or deserialization failure
     */
    public void load(String saveName) {
        long startTime = System.currentTimeMillis();

        try {
            SaveBundle bundle = backend.readSave(saveName);
            migrator.migrateToCurrentVersion(bundle, ManifestData.CURRENT_VERSION);

            // Clear existing persistent entities
            ImmutableArray<Entity> existing = engine.getEntitiesFor(
                Family.all(PersistenceIdComponent.class).get());
            Entity[] toRemove = new Entity[existing.size()];
            for (int i = 0; i < existing.size(); i++) {
                toRemove[i] = existing.get(i);
            }
            for (Entity e : toRemove) {
                engine.removeEntity(e);
            }

            double ox = originX();
            double oy = originY();
            double oz = originZ();

            Map<UUID, Entity> entityMap = new HashMap<>();

            // Restore player entity
            if (bundle.playerSnapshot != null) {
                Entity player = restoreEntity(bundle.playerSnapshot, ox, oy, oz);
                engine.addEntity(player);
                entityMap.put(bundle.playerSnapshot.entityId, player);
            }

            // Restore ship entities
            for (EntitySnapshot shipSnap : bundle.ownedShipSnapshots) {
                Entity ship = restoreEntity(shipSnap, ox, oy, oz);
                engine.addEntity(ship);
                entityMap.put(shipSnap.entityId, ship);
            }

            // Restore system/world entities
            for (List<EntitySnapshot> systemEntities : bundle.systemSnapshots.values()) {
                for (EntitySnapshot snap : systemEntities) {
                    Entity e = restoreEntity(snap, ox, oy, oz);
                    engine.addEntity(e);
                    entityMap.put(snap.entityId, e);
                }
            }

            // Second pass: wire up cross-entity references
            referenceResolver.resolve(engine, entityMap);

            long duration = System.currentTimeMillis() - startTime;
            eventBus.publish(new LoadCompleteEvent(saveName, duration));
        } catch (Exception e) {
            throw new RuntimeException("Load failed: " + saveName, e);
        }
    }

    /**
     * Call once per game frame. Increments the auto-save countdown and fires
     * {@link #triggerAutoSave()} when the interval elapses.
     *
     * @param deltaTime seconds since the last frame
     */
    public void update(float deltaTime) {
        if (!autoSaveEnabled) return;
        autoSaveTimer += deltaTime;
        if (autoSaveTimer >= autoSaveInterval) {
            autoSaveTimer = 0f;
            triggerAutoSave();
        }
    }

    /** Immediately saves to the next auto-save slot (round-robin across 3 slots). */
    public void triggerAutoSave() {
        String slotName = "autosave-" + autoSaveSlotIndex;
        autoSaveSlotIndex = (autoSaveSlotIndex + 1) % AUTO_SAVE_SLOT_COUNT;
        save(slotName);
    }

    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
    }

    public void setAutoSaveInterval(float seconds) {
        this.autoSaveInterval = seconds;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private double originX() {
        return coordinateManager != null ? coordinateManager.getOriginOffsetX() : 0.0;
    }

    private double originY() {
        return coordinateManager != null ? coordinateManager.getOriginOffsetY() : 0.0;
    }

    private double originZ() {
        return coordinateManager != null ? coordinateManager.getOriginOffsetZ() : 0.0;
    }

    private Entity restoreEntity(EntitySnapshot snapshot,
                                  double ox, double oy, double oz) {
        Entity entity = new Entity();
        entity.add(new PersistenceIdComponent(snapshot.entityId));

        // TransformComponent needs origin offsets — handle separately.
        TransformSnapshot ts = snapshot.getSnapshot("Transform", TransformSnapshot.class);
        if (ts != null) {
            TransformComponent tc = new TransformComponent();
            tc.restoreFromSnapshot(ts, ox, oy, oz);
            entity.add(tc);
        }

        // Restore all other Snapshotable components via the registry.
        for (Map.Entry<String, Object> entry : snapshot.componentSnapshots.entrySet()) {
            if ("Transform".equals(entry.getKey())) continue;
            Component component = SnapshotComponentRegistry.createAndRestore(
                entry.getKey(), entry.getValue());
            if (component != null) {
                entity.add(component);
            }
        }

        // Restore tag (marker) components.
        for (String tag : snapshot.tagComponents) {
            Component tagComponent = SnapshotComponentRegistry.createTag(tag);
            if (tagComponent != null) {
                entity.add(tagComponent);
            }
        }

        return entity;
    }
}
