package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.docking.DockingStateComponent;

import java.util.Map;
import java.util.UUID;

public class ReferenceResolver {
    private static final ComponentMapper<PlayerStateComponent> playerStateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<CombatAIComponent> combatAIMapper =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<DockingStateComponent> dockingMapper =
        ComponentMapper.getFor(DockingStateComponent.class);

    public void resolve(Engine engine, Map<UUID, Entity> entityMap) {
        ImmutableArray<Entity> entities = engine.getEntitiesFor(
            Family.all(PersistenceIdComponent.class).get());

        for (Entity entity : entities) {
            PlayerStateComponent ps = playerStateMapper.get(entity);
            if (ps != null) {
                ps.currentShip = resolveRef(ps.currentShipId, entityMap);
                ps.interactionTarget = resolveRef(ps.interactionTargetId, entityMap);
            }

            CombatAIComponent ai = combatAIMapper.get(entity);
            if (ai != null) {
                ai.currentTarget = resolveRef(ai.currentTargetId, entityMap);
            }

            DockingStateComponent dock = dockingMapper.get(entity);
            if (dock != null) {
                dock.targetEntity = resolveRef(dock.targetEntityId, entityMap);
            }
        }
    }

    private Entity resolveRef(UUID uuid, Map<UUID, Entity> entityMap) {
        if (uuid == null) return null;
        return entityMap.get(uuid);
    }
}
