package com.galacticodyssey.stealth;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.ShieldComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;

/**
 * Updates the player's {@link SignatureComponent} each frame based on the current ship state.
 *
 * <p>Reads {@link ShipFlightComponent} and {@link ShieldComponent} from {@code currentShip},
 * then writes {@code heatSignature}, {@code shieldsActive}, and {@code emSignature} to the
 * player's {@link SignatureComponent}.
 *
 * <p>Call {@link #setCurrentShip(Entity)} whenever the player boards or leaves a ship.
 * While {@code currentShip} is {@code null} this system is a no-op.
 */
public final class ShipSignatureSystem extends EntitySystem {

    private static final float HEAT_FACTOR = 0.8f;
    private static final float BASE_EM     = 0.05f;
    private static final float SHIELD_EM   = 0.40f;
    private static final float SCANNER_EM  = 0.30f;

    private static final ComponentMapper<SignatureComponent> SIG_M =
        ComponentMapper.getFor(SignatureComponent.class);
    private static final ComponentMapper<ShipFlightComponent> FLIGHT_M =
        ComponentMapper.getFor(ShipFlightComponent.class);
    private static final ComponentMapper<ShieldComponent> SHIELD_M =
        ComponentMapper.getFor(ShieldComponent.class);

    private static final Family PLAYER_FAMILY = Family
        .all(PlayerTagComponent.class, SignatureComponent.class).get();

    private ImmutableArray<Entity> playerEntities;
    private Entity currentShip; // set externally when player boards a ship

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(PLAYER_FAMILY);
    }

    /** Sets the ship entity whose flight/shield components drive the player's signature. */
    public void setCurrentShip(Entity ship) {
        this.currentShip = ship;
    }

    @Override
    public void update(float dt) {
        if (playerEntities.size() == 0 || currentShip == null) return;
        SignatureComponent sig = SIG_M.get(playerEntities.first());
        if (sig == null) return;

        ShipFlightComponent flight = FLIGHT_M.get(currentShip);
        ShieldComponent shield     = SHIELD_M.get(currentShip);

        if (flight != null) {
            sig.heatSignature = sig.darkMode ? 0f : flight.currentThrottle * HEAT_FACTOR;
        }

        sig.shieldsActive = (shield != null && shield.currentShield > 0f && !sig.darkMode);

        sig.emSignature = sig.darkMode ? BASE_EM * 0.05f
            : BASE_EM
                + (sig.shieldsActive ? SHIELD_EM : 0f)
                + (sig.scannerActive ? SCANNER_EM : 0f);
    }
}
