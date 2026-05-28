package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildfireSystemTest {

    static class StubEnv implements ThermalEnvironment {
        float o2 = 0.21f;
        public float ambientTemp(Vector3 p) { return 293f; }
        public float oxygenFraction(Vector3 p) { return o2; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private EventBus eventBus;
    private Engine engine;
    private StubEnv env;
    private FuelGridComponent grid;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        env = new StubEnv();
        engine = new Engine();
        engine.addSystem(new WildfireSystem(eventBus, env));

        grid = new FuelGridComponent(5, 5, 1f, 0f, 0f);
        for (int i = 0; i < grid.fuelLoad.length; i++) {
            grid.fuelLoad[i] = 10000f;
            grid.moisture[i] = 0f;
        }
        Entity gridEntity = new Entity();
        gridEntity.add(grid);
        engine.addEntity(gridEntity);
    }

    @Test
    void igniteAtEventStartsACellBurning() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f)); // strength >= 1 -> ignites immediately
        engine.update(0.1f);
        int idx = grid.index(2, 2);
        assertEquals(FuelGridComponent.BURNING, grid.state[idx]);
    }

    @Test
    void fireSpreadsToAdjacentCellOverTime() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 100; i++) engine.update(0.1f);
        // A 4-neighbour should have ignited (or burnt out) -- no longer pristine UNBURNT.
        assertNotEquals(FuelGridComponent.UNBURNT, grid.state[grid.index(2, 1)]);
    }

    @Test
    void burningCellConsumesFuelAndBurnsOut() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 500; i++) engine.update(0.1f);
        assertEquals(FuelGridComponent.BURNT, grid.state[grid.index(2, 2)]);
        assertTrue(grid.fuelLoad[grid.index(2, 2)] <= 0f);
    }

    @Test
    void noOxygenPreventsSpread() {
        env.o2 = 0.02f;
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 50; i++) engine.update(0.1f);
        assertEquals(FuelGridComponent.UNBURNT, grid.state[grid.index(0, 0)]);
    }
}
