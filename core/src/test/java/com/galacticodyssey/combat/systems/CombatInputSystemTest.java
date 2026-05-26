package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.PlayerTagComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatInputSystemTest {

    private CombatInputSystem system;
    private Engine engine;
    private Entity entity;

    private CombatInputComponent input;
    private FPSCameraComponent camera;

    @BeforeEach
    void setUp() {
        system = new CombatInputSystem();
        engine = new Engine();
        engine.addSystem(system);

        input  = new CombatInputComponent();
        camera = new FPSCameraComponent();

        entity = new Entity();
        entity.add(input);
        entity.add(camera);
        entity.add(new PlayerTagComponent());
        engine.addEntity(entity);
    }

    /**
     * With yaw=0 and pitch=0 the camera is looking straight ahead along -Z.
     * The computed aim direction should be approximately (0, 0, -1).
     */
    @Test
    void aimDirectionDerivedFromCameraAngles() {
        camera.yawAngle   = 0f;
        camera.pitchAngle = 0f;

        engine.update(0.016f);

        assertEquals( 0f, input.aimDirection.x, 1e-5f, "x should be 0");
        assertEquals( 0f, input.aimDirection.y, 1e-5f, "y should be 0");
        assertEquals(-1f, input.aimDirection.z, 1e-5f, "z should be -1 (forward)");
    }

    /**
     * A positive horizontal mouse delta (dx=50, dy=0) while requesting a melee
     * attack should resolve to {@link AttackDirection#RIGHT} and set
     * {@code meleeAttackRequested = true}.
     */
    @Test
    void meleeDirectionFromMouseDelta() {
        system.setMouseDeltaForMelee(50f, 0f);
        system.setMeleeAttackInput(true);

        engine.update(0.016f);

        assertTrue(input.meleeAttackRequested, "meleeAttackRequested should be true");
        assertEquals(AttackDirection.RIGHT, input.meleeAttackDirection,
            "Positive dx should resolve to RIGHT");
    }
}
