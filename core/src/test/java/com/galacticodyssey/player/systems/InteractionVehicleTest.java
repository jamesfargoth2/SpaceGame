package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.PlayerEnterVehicleEvent;
import com.galacticodyssey.core.events.PlayerExitVehicleEvent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.planet.terrain.GroundVehicleComponent;
import com.galacticodyssey.planet.terrain.VehicleEntryPointComponent;
import com.galacticodyssey.planet.terrain.VehicleTagComponent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InteractionVehicleTest {

    private Entity player(Vector3 pos) {
        Entity p = new Entity();
        p.add(new PlayerTagComponent());
        TransformComponent t = new TransformComponent();
        t.position.set(pos);
        p.add(t);
        p.add(new PlayerInputComponent());
        p.add(new PlayerStateComponent());
        // no PhysicsBodyComponent: freeze/unfreeze/teleport helpers must null-guard (they already do)
        return p;
    }

    private Entity vehicle(Vector3 pos) {
        Entity v = new Entity();
        v.add(new VehicleTagComponent());
        TransformComponent t = new TransformComponent();
        t.position.set(pos);
        v.add(t);
        v.add(new GroundVehicleComponent());
        VehicleEntryPointComponent entry = new VehicleEntryPointComponent();
        entry.triggerRadius = 3f;
        v.add(entry);
        return v;
    }

    @Test
    void pressingInteractNearVehicleEntersDriving() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicReference<PlayerEnterVehicleEvent> evt = new AtomicReference<>();
        bus.subscribe(PlayerEnterVehicleEvent.class, evt::set);
        InteractionSystem sys = new InteractionSystem(bus);
        engine.addSystem(sys);

        Entity p = player(new Vector3(0, 0, 0));
        Entity v = vehicle(new Vector3(1, 0, 0)); // within 3m
        engine.addEntity(p);
        engine.addEntity(v);

        p.getComponent(PlayerInputComponent.class).interactPressed = true;
        sys.update(1f / 60f);

        PlayerStateComponent st = p.getComponent(PlayerStateComponent.class);
        assertEquals(PlayerMode.DRIVING, st.currentMode);
        assertSame(v, st.currentVehicle);
        assertNotNull(evt.get());
    }

    @Test
    void pressingInteractWhileDrivingExits() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicReference<PlayerExitVehicleEvent> evt = new AtomicReference<>();
        bus.subscribe(PlayerExitVehicleEvent.class, evt::set);
        InteractionSystem sys = new InteractionSystem(bus);
        engine.addSystem(sys);

        Entity p = player(new Vector3(0, 0, 0));
        Entity v = vehicle(new Vector3(1, 0, 0));
        engine.addEntity(p);
        engine.addEntity(v);
        PlayerStateComponent st = p.getComponent(PlayerStateComponent.class);
        st.currentMode = PlayerMode.DRIVING;
        st.currentVehicle = v;

        p.getComponent(PlayerInputComponent.class).interactPressed = true;
        sys.update(1f / 60f);

        assertEquals(PlayerMode.ON_FOOT_EXTERIOR, st.currentMode);
        assertNull(st.currentVehicle);
        assertNotNull(evt.get());
    }
}
