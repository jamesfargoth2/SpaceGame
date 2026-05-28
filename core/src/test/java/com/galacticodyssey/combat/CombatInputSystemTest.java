package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.systems.CombatInputSystem;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CombatInputSystem}, verifying that grenade input flags
 * are correctly transferred from pending system state to {@link CombatInputComponent}.
 */
class CombatInputSystemTest {

    private Engine engine;
    private CombatInputSystem combatInputSystem;
    private Entity player;
    private CombatInputComponent inputComponent;

    @BeforeEach
    void setUp() {
        combatInputSystem = new CombatInputSystem();
        engine = new Engine();
        engine.addSystem(combatInputSystem);

        player = new Entity();
        inputComponent = new CombatInputComponent();
        player.add(inputComponent);
        player.add(new FPSCameraComponent());
        player.add(new PlayerTagComponent());

        engine.addEntity(player);
    }

    /**
     * Verifies the full grenade input lifecycle:
     * <ol>
     *   <li>After setting both grenade inputs and ticking once, both flags on the
     *       component are {@code true}.</li>
     *   <li>After a second tick (with no new input set), {@code grenadeThrowRequested}
     *       is cleared to {@code false} (one-shot), while {@code grenadeThrowHeld}
     *       remains {@code true} (persists).</li>
     * </ol>
     */
    @Test
    void grenadeThrowInputTransfersToComponent() {
        // --- Tick 1: set both grenade inputs, update engine ---
        combatInputSystem.setGrenadeThrowInput(true);
        combatInputSystem.setGrenadeThrowHeldInput(true);

        engine.update(0.016f);

        assertTrue(inputComponent.grenadeThrowRequested,
            "grenadeThrowRequested should be true after first tick");
        assertTrue(inputComponent.grenadeThrowHeld,
            "grenadeThrowHeld should be true after first tick");

        // --- Tick 2: no new input set; one-shot should clear, held should persist ---
        engine.update(0.016f);

        assertFalse(inputComponent.grenadeThrowRequested,
            "grenadeThrowRequested should be cleared (one-shot) after second tick");
        assertTrue(inputComponent.grenadeThrowHeld,
            "grenadeThrowHeld should still be true (held persists) after second tick");
    }
}
