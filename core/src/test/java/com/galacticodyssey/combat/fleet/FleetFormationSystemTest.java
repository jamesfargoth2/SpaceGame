package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetFormationSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetFormationSystemTest {

    private Engine engine;
    private FormationRegistry formationRegistry;
    private Entity fleetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        formationRegistry = new FormationRegistry();
        formationRegistry.registerDefaults(20);
        EventBus eventBus = new EventBus();
        engine.addSystem(new FleetFormationSystem(formationRegistry, eventBus));

        fleetEntity = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "test-fleet";
        fc.expanded = true;
        fleetEntity.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.formationTemplateId = "wedge";
        ffc.localAnchorX = 100f;
        ffc.localAnchorY = 0f;
        ffc.localAnchorZ = 200f;
        ffc.spacingScale = 1.0f;
        fleetEntity.add(ffc);
        engine.addEntity(fleetEntity);
    }

    @Test
    void shipMovesTowardFormationSlot() {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetEntity = fleetEntity;
        fmc.fleetId = "test-fleet";
        fmc.formationSlotIndex = 1;
        ship.add(fmc);
        TransformComponent tc = new TransformComponent();
        tc.position.set(0f, 0f, 0f);
        ship.add(tc);
        engine.addEntity(ship);

        engine.update(0.016f);

        Vector3 offset = fmc.localFormationOffset;
        assertFalse(offset.isZero(0.01f), "Formation offset should be computed");
    }
}
