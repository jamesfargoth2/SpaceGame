package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.VehicleRegistry;
import com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent;
import com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class VehicleBayServiceTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private static final String JSON = "{ \"vehicles\": [ { \"id\": \"rover_light\"," +
        " \"mass\": 900, \"maxHP\": 200, \"baySlots\": 1," +
        " \"weapon\": { \"damage\": 10, \"magSize\": 20 } } ] }";

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;

    /** Builds the single dynamics world for a test, retaining all sub-objects for disposal. */
    private btDiscreteDynamicsWorld newWorld() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        return world;
    }

    @AfterEach
    void tearDownWorld() {
        if (world != null) world.dispose();
        if (solver != null) solver.dispose();
        if (broadphase != null) broadphase.dispose();
        if (dispatcher != null) dispatcher.dispose();
        if (collisionConfig != null) collisionConfig.dispose();
    }

    private Entity ship(VehicleBayComponent bay) {
        Entity s = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(100, 0, 100);
        s.add(t);
        s.add(bay);
        return s;
    }

    @Test
    void deploySpawnsVehicleAndRemovesFromBay() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        EventBus bus = new EventBus();
        AtomicReference<VehicleDeployedEvent> evt = new AtomicReference<>();
        bus.subscribe(VehicleDeployedEvent.class, evt::set);

        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), bus);

        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 2;
        bay.storedVehicleIds.add("rover_light");
        bay.localRampSpawnPosition.set(0, 0, 6);
        Entity shipEntity = ship(bay);

        Entity vehicle = service.deploy(shipEntity, "rover_light");

        assertNotNull(vehicle);
        assertEquals(0, bay.storedVehicleIds.size());
        assertNotNull(evt.get());
        assertSame(vehicle, evt.get().vehicle);
        Vector3 pos = vehicle.getComponent(TransformComponent.class).position;
        assertEquals(106f, pos.z, 0.001f);
    }

    @Test
    void deployUnknownOrAbsentReturnsNull() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), new EventBus());
        VehicleBayComponent bay = new VehicleBayComponent();
        Entity shipEntity = ship(bay);

        assertNull(service.deploy(shipEntity, "rover_light")); // not in bay
    }

    @Test
    void retrieveReturnsToBayWhenCapacityAllows() {
        Engine engine = new Engine();
        VehicleRegistry reg = new VehicleRegistry();
        reg.loadFromJson(JSON);
        EventBus bus = new EventBus();
        AtomicReference<VehicleRetrievedEvent> evt = new AtomicReference<>();
        bus.subscribe(VehicleRetrievedEvent.class, evt::set);
        VehicleBayService service =
            new VehicleBayService(engine, newWorld(), reg, new VehicleFactory(), bus);

        VehicleBayComponent bay = new VehicleBayComponent();
        bay.capacity = 2;
        bay.storedVehicleIds.add("rover_light");
        Entity shipEntity = ship(bay);
        Entity vehicle = service.deploy(shipEntity, "rover_light");
        assertNotNull(vehicle);

        boolean ok = service.retrieve(shipEntity, vehicle);

        assertTrue(ok);
        assertEquals(1, bay.storedVehicleIds.size());
        assertEquals("rover_light", bay.storedVehicleIds.get(0));
        assertFalse(engine.getEntities().contains(vehicle, true));
        assertNotNull(evt.get());
    }
}
