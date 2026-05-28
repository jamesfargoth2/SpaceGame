package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetLODSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetLODSystemTest {

    @Test
    void nearbyShipGetsFullLOD() {
        Engine engine = new Engine();
        Vector3 playerPos = new Vector3(0f, 0f, 0f);
        engine.addSystem(new FleetLODSystem(new EventBus(), () -> playerPos));

        Entity ship = createShip(100f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(1.0f);

        FleetMemberComponent fmc = ship.getComponent(FleetMemberComponent.class);
        assertEquals(FleetMemberComponent.LODTier.FULL, fmc.lodTier);
    }

    @Test
    void distantShipGetsAbstractLOD() {
        Engine engine = new Engine();
        Vector3 playerPos = new Vector3(0f, 0f, 0f);
        engine.addSystem(new FleetLODSystem(new EventBus(), () -> playerPos));

        Entity ship = createShip(30000f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(1.0f);

        FleetMemberComponent fmc = ship.getComponent(FleetMemberComponent.class);
        assertEquals(FleetMemberComponent.LODTier.ABSTRACT, fmc.lodTier);
    }

    private Entity createShip(float x, float y, float z) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = "fleet-1";
        ship.add(fmc);
        TransformComponent tc = new TransformComponent();
        tc.position.set(x, y, z);
        ship.add(tc);
        CombatAIComponent ai = new CombatAIComponent();
        ship.add(ai);
        HealthComponent hp = new HealthComponent();
        ship.add(hp);
        return ship;
    }
}
