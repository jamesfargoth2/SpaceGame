package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;

import java.util.HashMap;
import java.util.Map;

public class ReputationManager implements ReputationQuery {

    private static final ComponentMapper<PlayerReputationComponent> REP_M =
        ComponentMapper.getFor(PlayerReputationComponent.class);
    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private final EventBus eventBus;
    private final ReputationConfigData config;
    private final Map<String, Map<String, PoliticalRelation>> relations;

    private Entity playerEntity;
    private PlayerReputationComponent reputationComponent;
    private PlayerStatsComponent statsComponent;

    public ReputationManager(EventBus eventBus, ReputationConfigData config,
                             Map<String, Map<String, PoliticalRelation>> relations) {
        this.eventBus = eventBus;
        this.config = config;
        this.relations = relations;

        eventBus.subscribe(ReputationChangeEvent.class, this::onReputationChange);
    }

    public void setPlayerEntity(Entity player) {
        this.playerEntity = player;
        this.reputationComponent = REP_M.get(player);
        this.statsComponent = STATS_M.get(player);
    }

    @Override
    public float getStanding(String factionId) {
        if (reputationComponent == null) return 0f;
        Float standing = reputationComponent.standings.get(factionId);
        return standing != null ? standing : 0f;
    }

    public ReputationTier getTier(String factionId) {
        return ReputationTier.fromStanding(getStanding(factionId));
    }

    private void onReputationChange(ReputationChangeEvent event) {
        if (reputationComponent == null) return;

        float delta = event.delta;
        int diplomacyLevel = getDiplomacyLevel();

        if (delta > 0f) {
            delta *= (1.0f + diplomacyLevel * config.diplomacyGainMultPerLevel);
        } else if (delta < 0f) {
            float reduction = Math.min(
                diplomacyLevel * config.diplomacyLossReductionPerLevel,
                config.diplomacyMaxLossReduction);
            delta *= (1.0f - reduction);
        }

        applyDelta(event.factionId, delta);

        Map<String, PoliticalRelation> factionRelations = relations.get(event.factionId);
        if (factionRelations != null) {
            for (Map.Entry<String, PoliticalRelation> entry : factionRelations.entrySet()) {
                float rippleFraction = getRippleFraction(entry.getValue());
                if (rippleFraction == 0f) continue;
                float sign = getRippleSign(entry.getValue());
                applyDelta(entry.getKey(), delta * rippleFraction * sign);
            }
        }
    }

    private void applyDelta(String factionId, float delta) {
        float oldStanding = getStanding(factionId);
        ReputationTier oldTier = ReputationTier.fromStanding(oldStanding);

        float newStanding = Math.max(-100f, Math.min(100f, oldStanding + delta));
        reputationComponent.standings.put(factionId, newStanding);

        ReputationTier newTier = ReputationTier.fromStanding(newStanding);
        if (oldTier != newTier) {
            eventBus.publish(new ReputationTierChangedEvent(
                factionId, oldTier, newTier, newStanding));
        }
    }

    private int getDiplomacyLevel() {
        if (statsComponent == null) return 0;
        Integer level = statsComponent.pointSkills.get(PointSkill.DIPLOMACY);
        return level != null ? level : 0;
    }

    private float getRippleFraction(PoliticalRelation relation) {
        return switch (relation) {
            case ALLIED -> 0.50f;
            case FRIENDLY -> 0.25f;
            case NEUTRAL -> 0.00f;
            case TENSE -> 0.10f;
            case HOSTILE -> 0.25f;
            case WAR -> 0.50f;
        };
    }

    private float getRippleSign(PoliticalRelation relation) {
        return switch (relation) {
            case ALLIED, FRIENDLY -> 1f;
            case NEUTRAL -> 0f;
            case TENSE, HOSTILE, WAR -> -1f;
        };
    }

    public void populateSaveData(Map<String, Object> factionState) {
        if (reputationComponent != null) {
            factionState.put("standings", new HashMap<>(reputationComponent.standings));
        }
    }

    @SuppressWarnings("unchecked")
    public void restoreFromSaveData(Map<String, Object> factionState) {
        if (reputationComponent == null) return;
        Object raw = factionState.get("standings");
        if (raw instanceof Map) {
            reputationComponent.standings.clear();
            reputationComponent.standings.putAll((Map<String, Float>) raw);
        }
    }
}
