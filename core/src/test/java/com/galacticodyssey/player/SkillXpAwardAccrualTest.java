package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import com.galacticodyssey.player.systems.SkillXpAwardSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillXpAwardAccrualTest {

    private Engine engine;
    private Entity player;
    private PlayerStatsComponent stats;
    private MovementStateComponent move;
    private PlayerStateComponent state;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        EventBus bus = new EventBus();
        RealTimeSkillSystem skills = new RealTimeSkillSystem(bus);
        engine.addSystem(skills);
        player = new Entity();
        stats = new PlayerStatsComponent();
        move = new MovementStateComponent();
        state = new PlayerStateComponent();
        player.add(stats); player.add(move); player.add(state);
        engine.addEntity(player);
        engine.addSystem(new SkillXpAwardSystem(bus, skills, engine));
    }

    @Test
    void sprintingAccruesAthletics() {
        move.isSprinting = true;
        move.isGrounded = true;
        move.currentSpeed = 5f;
        engine.update(1f); // 5 units this tick; need 10 per xp -> 0 yet
        engine.update(1f); // total 10 units -> 1 xp
        assertEquals(1f, stats.realTimeSkills.get(RealTimeSkill.ATHLETICS).xp, 0.01f);
    }

    @Test
    void pilotingAccruesPiloting() {
        state.currentMode = PlayerStateComponent.PlayerMode.PILOTING;
        engine.update(1f); // 1s -> 2 xp
        assertEquals(2f, stats.realTimeSkills.get(RealTimeSkill.PILOTING).xp, 0.01f);
    }

    @Test
    void idleAccruesNothing() {
        engine.update(1f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.ATHLETICS).xp, 0.01f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.PILOTING).xp, 0.01f);
    }
}
