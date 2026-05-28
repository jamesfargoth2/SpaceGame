package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.events.StatusEffectAppliedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ObjectConsumedByFireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombustionSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private ThermalMaterialRegistry registry;
    private final List<StatusEffectAppliedEvent> statusEvents = new ArrayList<>();
    private final List<ObjectConsumedByFireEvent> consumed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial grass = new ThermalMaterial();
        grass.id = "dry_grass"; grass.flammable = true; grass.burnHeatOutput = 4000f;
        grass.combustionEnergy = 8000f; grass.consumedWhenBurnt = true; grass.charMaterialId = "ash";
        registry.register(grass);

        engine = new Engine();
        engine.addSystem(new CombustionSystem(eventBus, registry));
        eventBus.subscribe(StatusEffectAppliedEvent.class, statusEvents::add);
        eventBus.subscribe(ObjectConsumedByFireEvent.class, consumed::add);
    }

    private Entity burnable(String materialId, boolean withHealth) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = materialId; t.material = registry.get(materialId);
        t.state = ThermalState.BURNING;
        e.add(tr); e.add(t);
        if (withHealth) { e.add(new HealthComponent()); e.add(new StatusEffectsComponent()); }
        engine.addEntity(e);
        return e;
    }

    @Test
    void ignitionEventAddsBurningComponentInitializedFromMaterial() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        BurningComponent b = e.getComponent(BurningComponent.class);
        assertNotNull(b);
        assertEquals(4000f, b.heatOutput, 0.001f);
        assertEquals(8000f, b.fuelRemaining, 4000f); // started at 8000, minus one tick
    }

    @Test
    void burningEntityDepositsHeatIntoOwnIncomingHeat() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        assertTrue(e.getComponent(TemperatureComponent.class).incomingHeat > 0f);
    }

    @Test
    void appliesBurningStatusOnceToEntityWithHealth() {
        Entity e = burnable("dry_grass", true);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        engine.update(0.016f);
        long burningApplied = statusEvents.stream()
                .filter(ev -> ev.effectType == StatusEffectType.BURNING).count();
        assertEquals(1, burningApplied, "BURNING status applied exactly once");
    }

    @Test
    void consumesEntityWhenFuelExhausted() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        // 8000 J fuel / (4000 W) = 2s; step well past it
        for (int i = 0; i < 200; i++) engine.update(0.016f);
        assertEquals(1, consumed.size());
        assertEquals("ash", consumed.get(0).charMaterialId);
    }
}
