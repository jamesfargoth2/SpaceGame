package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ResourceCollectedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import com.galacticodyssey.player.systems.SkillXpAwardSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillXpAwardSystemTest {

    private EventBus bus;
    private Entity player;
    private PlayerStatsComponent stats;

    @BeforeEach
    void setUp() {
        Engine engine = new Engine();
        bus = new EventBus();
        RealTimeSkillSystem skills = new RealTimeSkillSystem(bus);
        engine.addSystem(skills);
        player = new Entity();
        stats = new PlayerStatsComponent();
        player.add(stats);
        engine.addEntity(player);
        engine.addSystem(new SkillXpAwardSystem(bus, skills, engine));
    }

    @Test
    void ballisticDamageByPlayerAwardsFirearms() {
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, player, 100f, DamageType.BALLISTIC, HitRegion.TORSO));
        assertEquals(10f, stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.01f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.ENERGY_WEAPONS).xp, 0.01f);
    }

    @Test
    void energyDamageByPlayerAwardsEnergyWeapons() {
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, player, 50f, DamageType.ENERGY, HitRegion.TORSO));
        assertEquals(5f, stats.realTimeSkills.get(RealTimeSkill.ENERGY_WEAPONS).xp, 0.01f);
    }

    @Test
    void damageByNonPlayerAwardsNothing() {
        Entity npc = new Entity();
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, npc, 100f, DamageType.BALLISTIC, HitRegion.TORSO));
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.01f);
    }

    @Test
    void meleeHitByPlayerAwardsMelee() {
        Entity enemy = new Entity();
        bus.publish(new MeleeHitEvent(player, enemy, AttackDirection.OVERHEAD, HitRegion.HEAD, 40f, DamageType.MELEE));
        assertEquals(6f, stats.realTimeSkills.get(RealTimeSkill.MELEE).xp, 0.01f);
    }

    @Test
    void resourceCollectedAwardsMining() {
        bus.publish(new ResourceCollectedEvent("iron_ore", 3));
        assertEquals(6f, stats.realTimeSkills.get(RealTimeSkill.MINING).xp, 0.01f);
    }

    @Test
    void tradeAwardsTrading() {
        bus.publish(new TradeCompletedEvent("station_1", "water", 10, 5, 500, true));
        assertEquals(5f, stats.realTimeSkills.get(RealTimeSkill.TRADING).xp, 0.01f);
    }
}
