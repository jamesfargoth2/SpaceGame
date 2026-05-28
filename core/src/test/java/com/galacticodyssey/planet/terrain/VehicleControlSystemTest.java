package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleControlSystemTest {

    private Entity vehicle() {
        Entity v = new Entity();
        v.add(new TransformComponent());
        v.add(new GroundVehicleComponent());
        v.add(new CombatInputComponent());
        v.add(new VehicleTagComponent());
        return v;
    }

    private Entity player(PlayerMode mode, Entity vehicle, float fwd, float strafe, boolean fire) {
        Entity p = new Entity();
        p.add(new PlayerTagComponent());
        PlayerInputComponent in = new PlayerInputComponent();
        in.moveForward = fwd; in.moveStrafe = strafe;
        p.add(in);
        CombatInputComponent ci = new CombatInputComponent();
        ci.fireHeld = fire;
        p.add(ci);
        PlayerStateComponent st = new PlayerStateComponent();
        st.currentMode = mode; st.currentVehicle = vehicle;
        p.add(st);
        return p;
    }

    @Test
    void drivingRoutesInputToVehicle() {
        Engine engine = new Engine();
        VehicleControlSystem sys = new VehicleControlSystem();
        engine.addSystem(sys);
        Entity v = vehicle();
        engine.addEntity(v);
        engine.addEntity(player(PlayerMode.DRIVING, v, 1f, -1f, true));

        sys.update(1f / 60f);

        GroundVehicleComponent gv = v.getComponent(GroundVehicleComponent.class);
        assertEquals(1f, gv.throttleInput, 1e-4);
        assertEquals(-1f, gv.steerInput, 1e-4);
        assertTrue(v.getComponent(CombatInputComponent.class).fireHeld);
        assertTrue(v.getComponent(CombatInputComponent.class).aimDirection.len() > 0.5f);
    }

    @Test
    void notDrivingLeavesVehicleInputZero() {
        Engine engine = new Engine();
        VehicleControlSystem sys = new VehicleControlSystem();
        engine.addSystem(sys);
        Entity v = vehicle();
        engine.addEntity(v);
        engine.addEntity(player(PlayerMode.ON_FOOT_EXTERIOR, v, 1f, 1f, true));

        sys.update(1f / 60f);

        assertEquals(0f, v.getComponent(GroundVehicleComponent.class).throttleInput, 1e-4);
        assertFalse(v.getComponent(CombatInputComponent.class).fireHeld);
    }
}
