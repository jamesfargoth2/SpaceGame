package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.components.WeaponGroupComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponPilotSystemTest {

    @Test
    void firesActiveGroupWhenFireHeld() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        ShipWeaponSystem weaponSystem = new ShipWeaponSystem(eventBus);
        ShipWeaponPilotSystem system = new ShipWeaponPilotSystem(weaponSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(system);

        Entity ship = new Entity();
        WeaponGroupComponent groups = new WeaponGroupComponent();
        groups.groups[0].add("gun_left");
        groups.groups[0].add("gun_right");
        groups.activeGroup = 0;
        ship.add(groups);
        ship.add(new ShipHardpointComponent());
        ship.add(new TransformComponent());
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        flightInput.fireHeld[0] = true;
        engine.addEntity(player);

        // Should not crash — hardpoints don't exist so fireHardpoint returns false, but the system runs
        system.update(1f / 60f);
    }

    @Test
    void doesNotFireWhenNotHeld() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        ShipWeaponSystem weaponSystem = new ShipWeaponSystem(eventBus);
        ShipWeaponPilotSystem system = new ShipWeaponPilotSystem(weaponSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(system);

        Entity ship = new Entity();
        WeaponGroupComponent groups = new WeaponGroupComponent();
        groups.groups[0].add("gun_left");
        groups.activeGroup = 0;
        ship.add(groups);
        ship.add(new ShipHardpointComponent());
        ship.add(new TransformComponent());
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        // fireHeld[0] is false by default
        engine.addEntity(player);

        system.update(1f / 60f);
        // No crash, no fire
    }

    @Test
    void scrollDeltaCyclesActiveGroup() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        ShipWeaponSystem weaponSystem = new ShipWeaponSystem(eventBus);
        ShipWeaponPilotSystem system = new ShipWeaponPilotSystem(weaponSystem);
        engine.addSystem(weaponSystem);
        engine.addSystem(system);

        Entity ship = new Entity();
        WeaponGroupComponent groups = new WeaponGroupComponent();
        groups.groups[0].add("gun_left");
        groups.groups[1].add("missile_bay");
        groups.groups[2].add("turret_top");
        groups.activeGroup = 0;
        ship.add(groups);
        ship.add(new ShipHardpointComponent());
        ship.add(new TransformComponent());
        engine.addEntity(ship);

        Entity player = createPilotingPlayer(ship);
        ShipFlightInputComponent flightInput = player.getComponent(ShipFlightInputComponent.class);
        flightInput.scrollDelta = 1f; // scroll down = next group
        engine.addEntity(player);

        system.update(1f / 60f);

        assertEquals(1, groups.activeGroup, "Should cycle to next group");
    }

    private Entity createPilotingPlayer(Entity ship) {
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = ship;
        player.add(state);
        player.add(new ShipFlightInputComponent());
        return player;
    }
}
