package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.mission.job.ReputationQuery;
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

class ReputationManagerTest {

    private EventBus eventBus;
    private ReputationConfigData config;
    private Map<String, Map<String, PoliticalRelation>> relations;
    private ReputationManager manager;
    private Entity player;
    private PlayerReputationComponent repComp;
    private PlayerStatsComponent statsComp;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        config = new ReputationConfigData();
        relations = new HashMap<>();
        manager = new ReputationManager(eventBus, config, relations);

        player = new Entity();
        repComp = new PlayerReputationComponent();
        statsComp = new PlayerStatsComponent();
        player.add(repComp);
        player.add(statsComp);
        manager.setPlayerEntity(player);
    }

    @Test
    void implementsReputationQuery() {
        assertInstanceOf(ReputationQuery.class, manager);
    }

    @Test
    void unknownFactionReturnsZero() {
        assertEquals(0f, manager.getStanding("unknown_faction"));
    }

    @Test
    void basicStandingChange() {
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
        assertEquals(10f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingClampsToPositive100() {
        eventBus.publish(new ReputationChangeEvent("fed", 200f, "test"));
        assertEquals(100f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingClampsToNegative100() {
        eventBus.publish(new ReputationChangeEvent("fed", -200f, "test"));
        assertEquals(-100f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingAccumulates() {
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test1"));
        eventBus.publish(new ReputationChangeEvent("fed", 5f, "test2"));
        assertEquals(15f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void getTierReturnsCorrectTier() {
        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));
        assertEquals(ReputationTier.FRIENDLY, manager.getTier("fed"));
    }

    @Test
    void diplomacyBoostsGains() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 5);
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
        assertEquals(12.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void diplomacyReducesLosses() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 5);
        eventBus.publish(new ReputationChangeEvent("fed", -10f, "test"));
        assertEquals(-8.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void diplomacyLossReductionCapped() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 50);
        eventBus.publish(new ReputationChangeEvent("fed", -10f, "test"));
        assertEquals(-5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void tierCrossingPublishesEvent() {
        List<ReputationTierChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, events::add);

        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));

        assertEquals(1, events.size());
        assertEquals("fed", events.get(0).factionId);
        assertEquals(ReputationTier.NEUTRAL, events.get(0).oldTier);
        assertEquals(ReputationTier.FRIENDLY, events.get(0).newTier);
        assertEquals(30f, events.get(0).newStanding, 0.001f);
    }

    @Test
    void noEventWhenTierUnchanged() {
        List<ReputationTierChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, events::add);

        eventBus.publish(new ReputationChangeEvent("fed", 5f, "test"));

        assertTrue(events.isEmpty());
    }

    @Test
    void rippleToAlliedFaction() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(10f, manager.getStanding("confed"), 0.001f);
    }

    @Test
    void rippleToHostileFactionInvertsSign() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("pirates", PoliticalRelation.HOSTILE);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> pirateRelations = new HashMap<>();
        pirateRelations.put("fed", PoliticalRelation.HOSTILE);
        relations.put("pirates", pirateRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(-5f, manager.getStanding("pirates"), 0.001f);
    }

    @Test
    void rippleDoesNotCascade() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        confedRelations.put("terran", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);
        Map<String, PoliticalRelation> terranRelations = new HashMap<>();
        terranRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("terran", terranRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(10f, manager.getStanding("confed"), 0.001f);
        assertEquals(0f, manager.getStanding("terran"), 0.001f);
    }

    @Test
    void neutralRelationNoRipple() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("neutral_faction", PoliticalRelation.NEUTRAL);
        relations.put("fed", fedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(0f, manager.getStanding("neutral_faction"), 0.001f);
    }

    @Test
    void warRelationFullInverseRipple() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("enemy", PoliticalRelation.WAR);
        relations.put("fed", fedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(-10f, manager.getStanding("enemy"), 0.001f);
    }

    @Test
    void tenseRelationSmallInverseRipple() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("rival", PoliticalRelation.TENSE);
        relations.put("fed", fedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        // TENSE ripple: 20 * 0.10 * -1 = -2
        assertEquals(-2f, manager.getStanding("rival"), 0.001f);
    }

    @Test
    void noPlayerEntitySilentlyIgnores() {
        EventBus isolatedBus = new EventBus();
        ReputationManager orphan = new ReputationManager(isolatedBus, config, relations);
        isolatedBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
    }

    @Test
    void combatKillAppliesPenalty() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "npc_001";
        npc.add(npcId);

        eventBus.publish(new EntityKilledEvent(npc, player));

        assertEquals(config.combatKillPenalty, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void combatKillIgnoresNonFactionNpc() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.npcId = "npc_002";
        npc.add(npcId);

        eventBus.publish(new EntityKilledEvent(npc, player));

        assertEquals(0f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void combatKillIgnoresNonPlayerKiller() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "npc_003";
        npc.add(npcId);

        Entity otherNpc = new Entity();
        eventBus.publish(new EntityKilledEvent(npc, otherNpc));

        assertEquals(0f, manager.getStanding("fed"), 0.001f);
    }
}
