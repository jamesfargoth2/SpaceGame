package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.components.TransformComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Iterates all Ashley ECS entities that have a {@link PersistenceIdComponent} and builds
 * an {@link EntitySnapshot} for each, collecting state from every {@link Snapshotable}
 * component. {@link TransformComponent} is handled specially: it requires origin-offset
 * parameters to convert local float positions to galaxy-space doubles.  Components that
 * are neither {@code Snapshotable} nor {@code TransformComponent}/{@code PersistenceIdComponent}
 * are treated as tag components — their class name is recorded in the snapshot's tag set.
 */
public class EntitySnapshotBuilder {

    private static final ComponentMapper<PersistenceIdComponent> PERSISTENCE_MAPPER =
        ComponentMapper.getFor(PersistenceIdComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_MAPPER =
        ComponentMapper.getFor(TransformComponent.class);

    /**
     * Builds snapshots for every entity that carries a {@link PersistenceIdComponent}.
     *
     * @param engine        the Ashley engine whose entity list is scanned
     * @param originOffsetX X component of the floating-origin offset (galaxy-space)
     * @param originOffsetY Y component of the floating-origin offset (galaxy-space)
     * @param originOffsetZ Z component of the floating-origin offset (galaxy-space)
     * @return ordered list of entity snapshots (one per persistent entity)
     */
    public List<EntitySnapshot> buildSnapshots(Engine engine,
                                               double originOffsetX,
                                               double originOffsetY,
                                               double originOffsetZ) {
        List<EntitySnapshot> result = new ArrayList<>();

        ImmutableArray<Entity> entities =
            engine.getEntitiesFor(Family.all(PersistenceIdComponent.class).get());

        for (Entity entity : entities) {
            PersistenceIdComponent pid = PERSISTENCE_MAPPER.get(entity);
            EntitySnapshot snapshot = new EntitySnapshot(pid.uuid);

            // TransformComponent needs origin offsets — handle before the generic loop.
            TransformComponent tc = TRANSFORM_MAPPER.get(entity);
            if (tc != null) {
                snapshot.putSnapshot("Transform",
                    tc.takeSnapshot(originOffsetX, originOffsetY, originOffsetZ));
            }

            // Walk every other component on the entity.
            for (Component component : entity.getComponents()) {
                if (component instanceof TransformComponent) continue;
                if (component instanceof PersistenceIdComponent) continue;

                if (component instanceof Snapshotable<?>) {
                    // Strip the "Component" suffix to get a canonical type name.
                    String typeName = component.getClass().getSimpleName()
                        .replace("Component", "");
                    snapshot.putSnapshot(typeName, ((Snapshotable<?>) component).takeSnapshot());
                } else {
                    // Tag component — no mutable state, just record class presence.
                    snapshot.addTag(component.getClass().getSimpleName());
                }
            }

            result.add(snapshot);
        }

        return result;
    }
}
