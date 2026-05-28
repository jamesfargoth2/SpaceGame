package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectTemperatureSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private ThermalMaterialRegistry registry;
    private final List<IgnitionEvent> ignitions = new ArrayList<>();
    private final List<FreezeEvent> freezes = new ArrayList<>();

    /** Adjustable stub environment. */
    static class StubEnv implements ThermalEnvironment {
        float ambient = 293f, o2 = 0.21f;
        public float ambientTemp(Vector3 p) { return ambient; }
        public float oxygenFraction(Vector3 p) { return o2; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private final StubEnv env = new StubEnv();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial wood = new ThermalMaterial();
        wood.id = "wood"; wood.flammable = true; wood.ignitionPoint = 573f;
        registry.register(wood);
        ThermalMaterial flesh = new ThermalMaterial();
        flesh.id = "flesh"; flesh.freezable = true; flesh.freezePoint = 261f;
        registry.register(flesh);

        engine = new Engine();
        engine.addSystem(new ObjectTemperatureSystem(eventBus, registry, env));
        eventBus.subscribe(IgnitionEvent.class, ignitions::add);
        eventBus.subscribe(FreezeEvent.class, freezes::add);
    }

    private Entity object(String materialId, float temp) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = materialId; t.temperature = temp; t.thermalMass = 1000f; t.surfaceArea = 1f;
        e.add(tr); e.add(t);
        engine.addEntity(e);
        return e;
    }

    @Test
    void incomingHeatRaisesTemperatureThenResets() {
        Entity e = object("wood", 293f);
        TemperatureComponent t = e.getComponent(TemperatureComponent.class);
        t.incomingHeat = 100_000f; // W
        env.ambient = 293f;        // no radiative/conductive drive at ambient
        engine.update(0.1f);
        assertTrue(t.temperature > 293f, "100kW for 0.1s over 1000 J/K should raise temp");
        assertEquals(0f, t.incomingHeat, 0.0001f, "accumulator reset after integration");
    }

    @Test
    void coolsTowardColdAmbient() {
        Entity e = object("wood", 500f);
        TemperatureComponent t = e.getComponent(TemperatureComponent.class);
        env.ambient = 250f;
        engine.update(1.0f);
        assertTrue(t.temperature < 500f);
    }

    @Test
    void ignitesWhenAboveIgnitionPointWithOxygen() {
        Entity e = object("wood", 600f); // above 573 ignition
        env.o2 = 0.21f;
        engine.update(0.016f);
        assertEquals(1, ignitions.size());
        assertEquals(ThermalState.BURNING, e.getComponent(TemperatureComponent.class).state);
    }

    @Test
    void doesNotIgniteWithoutOxygen() {
        Entity e = object("wood", 600f);
        env.o2 = 0.02f; // too low
        engine.update(0.016f);
        assertTrue(ignitions.isEmpty());
    }

    @Test
    void freezesWhenBelowFreezePoint() {
        Entity e = object("flesh", 250f); // below 261 freeze
        engine.update(0.016f);
        assertEquals(1, freezes.size());
        assertEquals(ThermalState.FROZEN, e.getComponent(TemperatureComponent.class).state);
    }
}
