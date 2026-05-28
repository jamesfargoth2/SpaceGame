package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.HeatSourceComponent;
import com.galacticodyssey.planet.thermal.HeatSourceSystem;
import com.galacticodyssey.planet.thermal.ObjectTemperatureSystem;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThermalFireIntegrationTest {

    static class StubEnv implements ThermalEnvironment {
        public float ambientTemp(Vector3 p) { return 293f; }
        public float oxygenFraction(Vector3 p) { return 0.21f; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private Engine engine;
    private EventBus eventBus;
    private ThermalMaterialRegistry registry;
    private final List<IgnitionEvent> ignitions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial grass = new ThermalMaterial();
        grass.id = "dry_grass"; grass.flammable = true; grass.ignitionPoint = 533f;
        grass.burnHeatOutput = 4000f; grass.combustionEnergy = 8000f;
        grass.consumedWhenBurnt = true; grass.charMaterialId = "ash";
        registry.register(grass);
        ThermalMaterial ash = new ThermalMaterial();
        ash.id = "ash"; ash.ignitionPoint = 100000f;
        registry.register(ash);

        ThermalEnvironment env = new StubEnv();
        engine = new Engine();
        engine.addSystem(new WildfireSystem(eventBus, env));                       // 28
        engine.addSystem(new HeatSourceSystem());                                  // 29
        engine.addSystem(new CombustionSystem(eventBus, registry));                // 30
        engine.addSystem(new ObjectTemperatureSystem(eventBus, registry, env));    // 31
        eventBus.subscribe(IgnitionEvent.class, ignitions::add);
    }

    @Test
    void flamethrowerIgnitesGrassAndItBurnsOut() {
        // Target grass object.
        Entity grass = new Entity();
        grass.add(new TransformComponent());
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = "dry_grass"; t.material = registry.get("dry_grass");
        t.temperature = 293f; t.thermalMass = 200f; t.surfaceArea = 1f;
        grass.add(t);
        engine.addEntity(grass);

        // Flamethrower: strong heat emitter co-located with the grass.
        Entity flame = new Entity();
        TransformComponent ftr = new TransformComponent();
        ftr.position.set(0.2f, 0, 0);
        flame.add(ftr);
        HeatSourceComponent src = new HeatSourceComponent();
        src.power = 200_000f; src.radius = 2f; src.lifetime = 0.5f;
        flame.add(src);
        engine.addEntity(flame);

        // Run a few seconds.
        for (int i = 0; i < 300; i++) engine.update(0.05f);

        assertFalse(ignitions.isEmpty(), "grass should have ignited from the flamethrower");
        // Fuel (8000 J at 4000 W ~ 2s) exhausts -> consumed -> char material 'ash'.
        assertEquals("ash", t.materialId);
        assertEquals(ThermalState.NORMAL, t.state);
        assertNull(grass.getComponent(BurningComponent.class));
    }
}
