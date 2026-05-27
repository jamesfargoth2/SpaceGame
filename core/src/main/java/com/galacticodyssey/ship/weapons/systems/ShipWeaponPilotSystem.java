package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.WeaponGroupComponent;

import java.util.List;

/**
 * Bridges player fire inputs to the ship weapon system via weapon groups.
 *
 * <p>Reads {@link ShipFlightInputComponent#fireHeld} from the player entity and fires
 * all hardpoints in the corresponding {@link WeaponGroupComponent} group via
 * {@link ShipWeaponSystem#fireHardpoint}. Also handles cycling the active group
 * from {@link ShipFlightInputComponent#scrollDelta}.</p>
 *
 * <p>Only operates when the player is in {@link PlayerMode#PILOTING} mode.</p>
 */
public class ShipWeaponPilotSystem extends EntitySystem {

    private static final int PRIORITY = 7;
    private static final int GROUP_COUNT = 4;

    private final ShipWeaponSystem weaponSystem;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<ShipFlightInputComponent> flightInputMapper =
        ComponentMapper.getFor(ShipFlightInputComponent.class);
    private final ComponentMapper<WeaponGroupComponent> groupMapper =
        ComponentMapper.getFor(WeaponGroupComponent.class);

    private ImmutableArray<Entity> playerEntities;

    public ShipWeaponPilotSystem(ShipWeaponSystem weaponSystem) {
        super(PRIORITY);
        this.weaponSystem = weaponSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class,
            PlayerStateComponent.class,
            ShipFlightInputComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (playerEntities.size() == 0) return;

        Entity player = playerEntities.first();
        PlayerStateComponent state = stateMapper.get(player);
        if (state.currentMode != PlayerMode.PILOTING || state.currentShip == null) return;

        ShipFlightInputComponent input = flightInputMapper.get(player);
        if (input == null) return;

        Entity ship = state.currentShip;
        WeaponGroupComponent groups = groupMapper.get(ship);
        if (groups == null) return;

        // Scroll delta cycles the active weapon group
        if (Math.abs(input.scrollDelta) > 0.5f) {
            int direction = input.scrollDelta > 0 ? 1 : -1;
            groups.activeGroup = (groups.activeGroup + direction + GROUP_COUNT) % GROUP_COUNT;
        }

        // Fire held groups — fire group index corresponds to fireHeld index
        for (int g = 0; g < GROUP_COUNT; g++) {
            if (input.fireHeld[g]) {
                List<String> hardpoints = groups.groups[g];
                for (String hpId : hardpoints) {
                    weaponSystem.fireHardpoint(ship, hpId);
                }
            }
        }
    }
}
