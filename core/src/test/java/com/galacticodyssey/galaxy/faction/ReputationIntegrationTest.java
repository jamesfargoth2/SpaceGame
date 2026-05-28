package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReputationIntegrationTest {

    private EventBus eventBus;
    private ReputationManager manager;
    private Entity player;
    private PlayerReputationComponent repComp;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        ReputationConfigData config = new ReputationConfigData();

        Map<String, Map<String, PoliticalRelation>> relations = new HashMap<>();
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        fedRelations.put("pirates", PoliticalRelation.HOSTILE);
        relations.put("fed", fedRelations);

        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);

        Map<String, PoliticalRelation> pirateRelations = new HashMap<>();
        pirateRelations.put("fed", PoliticalRelation.HOSTILE);
        relations.put("pirates", pirateRelations);

        manager = new ReputationManager(eventBus, config, relations);

        player = new Entity();
        repComp = new PlayerReputationComponent();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.pointSkills.put(PointSkill.DIPLOMACY, 0);
        player.add(repComp);
        player.add(stats);
        manager.setPlayerEntity(player);
    }

    @Test
    void killFactionNpcRipplesToAlliesAndEnemies() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        List<ReputationTierChangedEvent> tierEvents = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, tierEvents::add);

        eventBus.publish(new EntityKilledEvent(npc, player));

        // Primary: fed = -15
        assertEquals(-15f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.UNFRIENDLY, manager.getTier("fed"));

        // Ripple to ALLIED confed: -15 * 0.50 * +1 = -7.5
        assertEquals(-7.5f, manager.getStanding("confed"), 0.001f);

        // Ripple to HOSTILE pirates: -15 * 0.25 * -1 = +3.75
        assertEquals(3.75f, manager.getStanding("pirates"), 0.001f);

        // Tier crossing event for fed: NEUTRAL → UNFRIENDLY
        assertTrue(tierEvents.stream().anyMatch(e ->
            e.factionId.equals("fed")
            && e.oldTier == ReputationTier.NEUTRAL
            && e.newTier == ReputationTier.UNFRIENDLY));
    }

    @Test
    void multipleKillsAccumulateToHostile() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        List<ReputationTierChangedEvent> tierEvents = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, tierEvents::add);

        // Kill 4 NPCs: 4 * -15 = -60 → HOSTILE
        for (int i = 0; i < 4; i++) {
            eventBus.publish(new EntityKilledEvent(npc, player));
        }

        assertEquals(-60f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.HOSTILE, manager.getTier("fed"));

        assertTrue(tierEvents.stream().anyMatch(e ->
            e.factionId.equals("fed")
            && e.newTier == ReputationTier.HOSTILE));
    }

    @Test
    void missionRewardGainsReputation() {
        eventBus.publish(new ReputationChangeEvent("fed", 25f, "mission:test_001"));

        assertEquals(25f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.FRIENDLY, manager.getTier("fed"));
    }

    @Test
    void diplomacySkillAffectsKillPenalty() {
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        stats.pointSkills.put(PointSkill.DIPLOMACY, 10);
        manager.setPlayerEntity(player);

        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        eventBus.publish(new EntityKilledEvent(npc, player));

        // Kill penalty = -15, reduction = min(10 * 0.03, 0.5) = 0.3
        // Effective = -15 * (1 - 0.3) = -10.5
        assertEquals(-10.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void saveAndRestorePreservesFullState() {
        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));
        eventBus.publish(new ReputationChangeEvent("pirates", -20f, "test"));

        Map<String, Object> factionState = new HashMap<>();
        manager.populateSaveData(factionState);

        // Create a new manager and player
        ReputationManager newManager = new ReputationManager(
            new EventBus(), new ReputationConfigData(), new HashMap<>());
        Entity newPlayer = new Entity();
        PlayerReputationComponent newRep = new PlayerReputationComponent();
        newPlayer.add(newRep);
        newPlayer.add(new PlayerStatsComponent());
        newManager.setPlayerEntity(newPlayer);

        newManager.restoreFromSaveData(factionState);

        // fed = 30 (direct) + 5 (ripple from pirates -20 via HOSTILE: -20 * 0.25 * -1 = +5) = 35
        assertEquals(35f, newManager.getStanding("fed"), 0.001f);
        // pirates = -20 (direct) + ripple from fed +30 via HOSTILE: 30 * 0.25 * -1 = -7.5 → -27.5
        assertEquals(-27.5f, newManager.getStanding("pirates"), 0.001f);
    }
}
